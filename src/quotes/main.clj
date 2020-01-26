(ns quotes.main
  (:require [aleph.http]
            [hiccup.core]
            [hiccup.page]
            [reitit.ring]
            [riemann.client]))

;; * Quotes

(def quotes
  [{:text   "If we're not solving problems, what are we doing?"
    :person "Rich Hickey"}
   {:text   "Good design has to account for all of the pieces, not just some."
    :person "Stuart Halloway"}
   {:text   "I’m never going back to a non-LISP. Life is too short to learn fussy language grammars with tons of special cases."
    :person "Gene Kim"}])

(defn wrap-random-quote
  "Provides a (pseudo)randomized quote to display on the page."
  [handler]
  (fn [request]
    (handler (assoc request :quote (rand-nth quotes)))))

;; * Sending events to Riemann

(def riemann-client (riemann.client/tcp-client {:host "0.0.0.0"
                                                :port 5555}))

(defn wrap-riemann-service-time
  "Sends time to service each request to Riemann."
  [handler]
  (fn [request]
    (let [start    (System/nanoTime)
          response (handler request)
          end      (System/nanoTime)
          duration (- end start)]
      (-> riemann-client
          (riemann.client/send-event {:service "quotes.http.index.service-time"
                                      :metric  duration
                                      :state   "ok"})
          (deref 5000 ::timeout))
      response)))

;; * HTTP handlers

(defn index-page-handler
  [request]
  (let [{:keys [text person]} (:quote request)]
    {:status 200
     :body   (hiccup.core/html
              (hiccup.page/html5
               [:head
                [:title "quotes"]
                [:meta {:charset "utf-8"}]]
               [:body
                [:h1 "Cool Quotes"]
                [:p [:span "\""] [:strong text] [:span "\""]]
                [:p [:span " - "] person]]))}))

(def app
  (reitit.ring/ring-handler
   (reitit.ring/router
    ["/" {:get        index-page-handler
          :middleware [[wrap-riemann-service-time]
                       [wrap-random-quote]]}])
   (reitit.ring/create-default-handler)))

;; * HTTP server

(defonce *server (atom nil))

(defn start-server!
  [port]
  (reset! *server (aleph.http/start-server app {:port port})))

(defn stop-server!
  []
  (.close @*server)
  (reset! *server nil))

;; * App entry point

(defn -main [& {:keys [port]
                :or   {port 8080}}]
  (println (str "Starting HTTP server on " port))
  (start-server! port)
  (println (str "Started HTTP server")))
