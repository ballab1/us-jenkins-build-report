;; If available on a classpath root, user.clj will be loaded
;; regardless of -i options passed to clj|clojure.
(ns user
  (:require [clojure.core :refer (find re-find)]
            [clojure.java.javadoc :as jd]
            [clojure.pprint :refer (pprint) :rename {pprint ppr}]
            [clojure.repl :refer (doc source apropos)]
            [clojure.tools.trace :refer :all]))

(defn ns-avail
  "List either all symbols or those matching a regular
  expression in the current or provided namespace
  e.g.: (ns-avail :re #\"\\.*zip\\.*\" :ans 'clojure.core)"
  [& {:keys [re ans] :or {re #"\.*" ans *ns*}}]
  (sort (filter #(re-find re (str %)) (keys (ns-map ans)))))

(defmacro dlet
  "Convenience macro to debug let bindings."
  [bindings & body]
  `(let [~@(mapcat (fn [[n v]]
                     (if (or (vector? n) (map? n))
                       [n v]
                       [n v '_ `(println (name '~n) ":" ~v)]))
                   (partition 2 bindings))]
     ~@body))

#_(clojure.main/repl
   :prompt (fn []) ;; prompt is handled by line-reader
   :read (rebel-readline.core/clj-repl-read
          (rebel-readline.core/line-reader
           (rebel-readline.service.local-clojure/create))))

(defn reset-doc-roots
  "Override clojure.java.javadoc dynamic vars to get more recent document
  references with vim-fireplace 'K'."
  []
  #_(alter-var-root
     #'clojure.java.javadoc/*feeling-lucky*
     (constantly true))

  (alter-var-root
   #'clojure.java.javadoc/*core-java-api*
   (constantly
    (let [jsv (System/getProperty "java.specification.version")]
      (case jsv
        "1.6" "http://docs.oracle.com/javase/6/docs/api/"
        "1.7" "http://docs.oracle.com/javase/7/docs/api/"
        "1.8" "http://docs.oracle.com/javase/8/docs/api/"
        (str "http://docs.oracle.com/javase/" jsv "/docs/api/")))))

  (alter-var-root
   #'clojure.java.javadoc/*remote-javadocs*
   (constantly
    (ref (sorted-map
          "com.google.common." "http://google.github.io/guava/releases/23.0/api/docs/"
          "java." clojure.java.javadoc/*core-java-api*
          "javax." clojure.java.javadoc/*core-java-api*
          "org.ietf.jgss." clojure.java.javadoc/*core-java-api*
          "org.omg." clojure.java.javadoc/*core-java-api*
          "org.w3c.dom." clojure.java.javadoc/*core-java-api*
          "org.xml.sax." clojure.java.javadoc/*core-java-api*
          "org.apache.commons.codec." "http://commons.apache.org/proper/commons-codec/apidocs/"
          "org.apache.commons.io." "http://commons.apache.org/proper/commons-io/javadocs/api-release/"
          "org.apache.commons.lang." "http://commons.apache.org/proper/commons-lang/javadocs/api-2.6/"
          "org.apache.commons.lang3." "http://commons.apache.org/proper/commons-lang/javadocs/api-release/"))))

  (jd/add-remote-javadoc "com.fasterxml.uuid."
                         "http://cowtowncoder.github.io/java-uuid-generator/javadoc/3.1.3/")
  (jd/add-remote-javadoc "com.zaxxer.hikari." "http://static.javadoc.io/com.zaxxer/HikariCP/3.2.0/")
  (jd/add-remote-javadoc "org.apache.kafka." "https://kafka.apache.org/20/javadoc/index.html?")
  (jd/add-remote-javadoc "org.postgresql." "https://jdbc.postgresql.org/documentation/publicapi/"))

(reset-doc-roots)
