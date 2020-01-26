(ns quotes.main
  (:require [aleph.http]
            [hiccup.core]
            [hiccup.page]
            [reitit.ring]))

(defn index-page-handler
  [get-quote _request]
  (let [{:keys [text person] :as _quote} (get-quote)]
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

(def quotes
  [{:text   "If we're not solving problems, what are we doing?"
    :person "Rich Hickey"}
   {:text   "Good design has to account for all of the pieces, not just some."
    :person "Stuart Halloway"}
   {:text   "I’m never going back to a non-LISP. Life is too short to learn fussy language grammars with tons of special cases."
    :person "Gene Kim"}])

(def app
  (reitit.ring/ring-handler
   (reitit.ring/router
    ["/" {:get (partial index-page-handler (fn []
                                             (rand-nth quotes)))}])
   (reitit.ring/create-default-handler)))

(defonce *server (atom nil))

(defn start-server!
  [port]
  (reset! *server (aleph.http/start-server app {:port port})))


(defn stop-server!
  []
  (.close @*server)
  (reset! *server nil))

(defn -main [& {:keys [port]
                :or   {port 8080}}]
  (println (str "Starting HTTP server on " port))
  (start-server! port)
  (println (str "Started HTTP server")))
