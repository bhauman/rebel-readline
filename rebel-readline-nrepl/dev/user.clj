(ns user)
(require '[nrepl.server :refer [start-server stop-server]])

(println "Starting NREPL server at 7888")
(defonce server (start-server :port 7888))
