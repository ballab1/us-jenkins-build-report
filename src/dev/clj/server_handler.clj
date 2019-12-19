;; See: https://github.com/bhauman/lein-figwheel/blob/master/examples
;; https://github.com/ring-clojure/ring
;; https://github.com/weavejester/compojure
(ns server-handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]))

(defroutes app-routes
  ;; This will deliver all of the assets from the public directory
  ;; under resources i.e. resources/public. "resources" needs to be
  ;; in the classpath.
  (route/resources "/" {:root "public"})
  (GET "/" [] (response/content-type
               (response/resource-response "index.html" {:root "public"})
               "text/html"))
  (GET "/hello" [] "Hello World!")
  (route/not-found "Not Found!"))

(def app (wrap-defaults app-routes site-defaults))

;; this development application has a var reference to the app-routes above
;; for friendlier REPL based reloading
(def dev-app (wrap-defaults #'app-routes site-defaults))
