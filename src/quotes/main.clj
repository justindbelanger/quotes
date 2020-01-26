(ns quotes.main
  (:require [aleph.http]
            [hiccup.core]
            [hiccup.page]
            [juxt.clip.core :as clip]
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

(defn make-riemann-client
  "Connects to a running Riemann instance via TCP.
  Uses the default host and port.
  Returns the Riemann client instance."
  []
  (riemann.client/tcp-client {:host "0.0.0.0"
                              :port 5555}))

(defn wrap-riemann-service-time
  "Sends time to service each request to Riemann."
  [handler riemann-client]
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
  "Serves an HTML page containing the inspirational `:quote`
  provided in the `request` map."
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

(defn make-app
  "Provides a Ring-compatible handler to serve the Quotes app.
  Sends metrics using the given `riemann-client`."
  [riemann-client]
  (reitit.ring/ring-handler
   (reitit.ring/router
    ["/" {:get        index-page-handler
          :middleware [[wrap-riemann-service-time riemann-client]
                       [wrap-random-quote]]}])
   (reitit.ring/create-default-handler)))

;; * Clip config

(defn make-system-config
  "Provides a Clip system config using the given HTTP `port`."
  [port]
  {:components
   {:riemann-client {:start `make-riemann-client}
    :app            {:start `(make-app (clip/ref :riemann-client))}
    :server         {:start `(aleph.http/start-server (clip/ref :app)
                                                      {:port ~port})}}})

;; * App entry point

(def *system-config (atom nil))
(def *system (atom nil))

(defn start-all! [port]
  (let [system-config (make-system-config port)
        system        (clip/start system-config)]
    (reset! *system system)
    (reset! *system-config system-config)))

(defn stop-all! []
  (clip/stop @*system-config @*system)
  (reset! *system nil)
  (reset! *system-config nil))

(defn -main [& {:keys [port]
                :or   {port 8080}}]
  (println (str "Starting HTTP server on " port))
  (start-all! port)
  (println (str "Started HTTP server")))
