(in-ns 'rebel-readline.clojure.main)

(defn- load-init-script
  [init-script]
  (let [cl (.getContextClassLoader (Thread/currentThread))]
    (.setContextClassLoader (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl))  ; Required for clojure.repl.deps/add-libs (1.12+)
    (binding [*repl* true]                                                                 ; Required for clojure.repl.deps/add-libs (1.12+)
      (load-file init-script))))
