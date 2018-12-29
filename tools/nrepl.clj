(ns nrepl
  (:require
   [nrepl.server :as nrepl]
   [cider.nrepl :as ci]))

(defn -main [& args]
  (nrepl/start-server :port 7888 :handler ci/cider-nrepl-handler)
  (println "started at port" 7888))
