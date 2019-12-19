(ns config.env
  (:require [aero.core :as aero]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [manifest.core :as manifest])
  (:gen-class))

(def config (atom nil))

(defn run-dev-mode []
  (or (System/getenv "RUN_DEV_MODE") false))

(defn read-config
  "Read config.edn from the classpath creating a configuration based on profile"
  [profile]
  ; If a java string is passed, make it a keyword, keywords stay keywords.
  (aero/read-config (io/resource "config.edn") {:profile (keyword profile)}))

(defn init-config
  "To read a config file as a resource in the jar that is 'runnning', configuration
  needs to be called from that jar. Hence, loading this lib's namespace cannot
  initialize as it would access uS-lib.jar for config.edn. Hence, we need to make
  initialization a function in uS-lib to be called from the uService jar."
  [main-ns]
  (reset! config
          (read-config (or (when-let [cfg-prof (System/getenv "CONFIG_PROFILE")]
                                    ; env vars are simple strings. Make it an actual
                                    ; clojure type
                             (edn/read-string cfg-prof))
                           :dev)))
  (swap! config assoc :main-ns (str main-ns))
  (when (run-dev-mode)
    (pp/pprint @config)))

(defn main-ns []
  (:main-ns @config))

(defn webserver-port []
  (get-in @config [:webserver :port]))

(defn client-id []
  (:client-id @config))

(defn group-id []
  (:group-id @config))

(defn db-url []
  (get-in @config [:db :url]))

(defn db-user []
  (get-in @config [:db :user]))

(defn db-password []
  (get-in @config [:db :password]))

(defn bootstrap-servers []
  (get-in @config [:kafka :bootstrap-servers]))

(defn topic []
  (:topic @config))

(defn dlq-topic []
  (str (:topic @config) "-dlq"))

(defn auto-commit []
  (:auto-commit @config))

(defn hb-topic []
  (:hb-topic @config))

(defn log-dir []
  (or (get-in @config [:log :dir]) "log"))

(defn max-log-size-bytes []
  (or (get-in @config [:log :max-log-size-bytes]) (* 1024 1024 10)))  ; Make default 10 Megs

(defn max-log-files []
  (or (get-in @config [:log :max-log-files]) 10))  ; Make default 10 log files

(defn timbre-log-level []
  ; handle env var string to keyword conversion automatically.
  (or (if-let [level (get-in @config [:log :level])]
        ; Make a string or keyword a keyword.
        (keyword level))
      :debug))

(defn timbre-ns-whitelist []
  (get-in @config [:log :ns-whitelist] []))

(defn timbre-ns-blacklist []
  (get-in @config [:log :ns-blacklist] []))

(def get-manifest
  "Keywordizes the manifest, assumed to be on the classpath"
  (memoize
   (fn [some-key]
      ;; Provide main Java class name, so use _ for -.
     (manifest/manifest some-key))))

(def get-jre
  (memoize
   (fn []
     (let [jv (System/getProperty "java.version")
           vendor (System/getProperty "java.vendor")
           vm-version (System/getProperty "java.vm.version")
           jre (format "%s (%s %s)" jv vendor vm-version)]
       jre))))

(def revision
  (memoize
   (fn []
     (if-let [revision (:SCM-Revision (get-manifest (main-ns)))]
       revision
       (if (.exists (io/file ".revision"))
         (if-let [revision (clojure.string/trim-newline (slurp ".revision"))]
           (if (str/blank? revision)
             "No-SCM"
             revision))
         (let [result (shell/sh "git" "describe" "--tag" "--dirty")]
           (if (zero? (:exit result))
             (clojure.string/trim (:out result))
             "No-SCM")))))))

(def app-name
  (memoize
   (fn []
     (if-let [title (:Title (get-manifest (:main-ns @config)))]
       (str (if (str/blank? title) (:client-id @config) title) "-" (revision))
       (str (:client-id @config) "-" (revision))))))
