(ns medlist.core
  (:require
   [clj-pdf.core :as p]
   [clj-http.client :as client]

   [cheshire.core :as json]
   [clojure.java.io :as io]
   [pdfboxing.text :as pdf]
   [clojure.string :as str]
   [clojure.java.io]))

(defn links-for [p]
  (let [m (re-matcher #"href=\"([^\"]*)\"" p)]
    (loop [res []
           match (second (re-find m))]
      (if (nil? match)
        res
        (recur (conj res match) (second (re-find m)))))))

(defonce pdf-links
  (->>
   "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЭЮЯ"
   (mapcat (fn [c]
             (links-for (:body (client/get "http://www.rspor.ru/index.php?mod1=preparats&mod2=db2&cmd=list" {:simbol (str c)})))))
  (filter #(str/includes? % "db_preparats"))
  distinct))

(defn load-pdfs []
  (doseq [[i p] (map-indexed (fn [a b] [a b]) pdf-links)]
    (try
      (p/collate {:title "my-doc"}
                 (clojure.java.io/output-stream (clojure.java.io/file (str "backend/resources/drugs/" i ".pdf")))
                 (:body (client/get p {:as :byte-array})))
      (catch Exception e
        (prn "Error: " (pr-str e))))))

(defn parse-segments [d]
  (into {} (map-indexed (fn [i v] [i v])
                        (str/split d #"\s\n[1-9]+\.?" ))))

(defn extract-segments []
  (map
   (fn [p]
     (try
       (let [text (pdf/extract p)]
         (parse-segments text))
       (catch Exception e
         (prn "error: " (pr-str e)))))
   (rest (file-seq (clojure.java.io/file "backend/resources/drugs")))))

(def result
  (->> (extract-segments)
       (remove #(or (nil? (get % 5))
                    (nil? (get % 2))
                    (nil? (get % 0))
                    (nil? (get % 3)))
               )
       )
  )

(count result)

{:name "..."
 :trademark []
 :effectiveness "..."
 :group "..."}


(def mapper
  {:name
   (fn [idx]
     (->> (get idx 0)
          (re-seq #"\b[А-Я]+\b")
          (map str/lower-case)
          (str/join " ")
          (str/capitalize)))
   :trademark
   (fn [idx]
     (->> (str/split (get idx 2) #",")
          (mapv str/trim)
          (mapv #(str/replace % "." ""))))
   :effectiveness
   (fn [idx]
     (let [ef (get idx 5)]
       (let [l (second (re-find #"\bдоказательств[а]?\b ([АВСЕABСDE])" ef))]
         (cond-> {:summary ef}
           l (assoc :level l)))))
   :group
   (fn [idx] (get idx 3))})

(defn get-resources []
  (map
   (fn [r]
     (->> mapper
          (map (fn [[k v]]
                 [k (apply v [r])]))
          (into {})))
   result))

(def resource (first (shuffle (get-resources))))

(doall
 (map
  (fn [r]
    (try
      (client/post "http://localhost:8080/MedicationReport"
                  {:content-type :json
                   :body (json/generate-string r)})
      (catch Exception e
        (prn e)))
    )
  (get-resources))
 )

(spit
 "/tmp/resources.json"
 (json/generate-string
  (get-resources)
  {:pretty true}))

((:effectiveness mapper)
 (get (vec result) 25))

(str/trim " s ")

(->>
 result
 (map (fn [v] {:name (get v 0)
               :ef ((:effectiveness mapper) v)}))
 (filter #(nil? (get-in % [:ef :level])))
 first
 )

(re-find #"\bдоказательств[а]?\b ([АВСЕABСDE])"
         

         " Уровень убедительности доказательства А. Имеются доказательства клинической эффективности невирапина при ВИЧ -\nинфекции. Показано, что невирапин эффективнее зидовудина снижает частоту развития ВИЧ-инфекции у детей при его \nприменении у ВИЧ-инфицированных матерей и их детей.")

(spit
 "/tmp/names.json"
 (json/generate-string
  (->>
   result
   (map (fn [v] ((:effectiveness mapper) v)))
   (map (fn [n] {:trademark (:level n)}))
   vec)
  {:pretty true}
  )

 )

(def temp
  (->>
   (keys result)
   (map (fn [n]
          (str/capitalize (str/join " " (map str/lower-case (re-seq #"\b[А-Я]+\b" n))))))
   (remove empty?)
   vec))

(comment

  (load-pdfs)

  )

