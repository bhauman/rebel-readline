(ns user)
#_(require '[nrepl.server :refer [start-server stop-server]])

#_(println "Starting NREPL server at 7888")
#_(defonce server (start-server :port 7888))

(add-tap (fn [x] (spit "DEBUG.log" (prn-str x)  :append true)))

(defn long-runner []
  (prn "hey"))
