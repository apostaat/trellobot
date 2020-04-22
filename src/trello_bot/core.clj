(ns trello-bot.core
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [morse.handlers :as h]
            [morse.polling :as p]
            [morse.api :as t]
            [cheshire.core :refer :all :as json]
            [send-mail :as g ]
            [clj-http.client :as http])
            (:gen-class))
  

(def token "secret ")
(def db (atom {}))
(def db-file "pathtofile")
(def e-mail "e-mail")
(def secret "secret-password" )
(def base-url "https://api.telegram.org/bot")

(when
  (not (empty? (slurp db-file)))
  (reset! db (read-string (slurp db-file))))


(defn send-url-button
  "Sends URL-button to the chat"
  ([token chat-id]
   (let [url  (str base-url token "/sendMessage")
         body {:chat_id chat-id :text "To create Trello e-mail use the link below. \n To create task in TrelloBot:\n Taskname \n Then enter task body using newline."
               :reply_markup (json/generate-string
                               {:inline_keyboard
                                [[{:text "How to create Trello board e-mail (official docs)."
                                   :url "https://help.trello.com/article/809-creating-cards-by-email" }]]})}
         resp (http/post url {:content-type :json
                              :as           :json
                              :form-params  body})]
     (-> resp :body))))

(h/defhandler handler
  (h/command-fn "start"
    (fn [{{id :id name :first_name :as chat} :chat}]
      (println "This one is connected: " chat )
      (if-let  [got-it (get @db (str id))]
        (t/send-text token id (str "Hello, " name "!" " Welcome to TrelloTaskBot. If this e-mail is NO LONGER valid: " got-it " - send us your e-mail via sending message with e-mail to bot."
                                 ))
        (t/send-text token id (str "Hello, " name "! " "Welcome to TrelloTaskBot. You're new user, so we need your Trello board e-mail." )))))

  (h/command-fn "help"
    (fn [{{id :id :as chat} :chat}]
      (println "Help was requested in " chat)
      (send-url-button token id)))


  (h/message-fn
    (fn [{{id :id}  :chat :as message}]
      (let [text (:text message)]
        (println "Intercepted message: " message)
        (if (str/ends-with? text "@boards.trello.com" )
          (do (swap! db assoc (str id) text )
              (t/send-text token id (str "This e-mail: " text " will be used as for sending tasks to Trello."))
              (spit db-file @db))
           (let [ split (str/split text #"\n")
                  check-split  (count split)
                  subject (first split)
                  body (second split)
                  recepient (get @db (str id))]
            (case check-split
              1 (if (some #(= % \@) message )
                  (t/send-text token id "It's not a valid Trello e-mail address. Use /help to get one.")
                  (t/send-text token id "It's not a valid task message. Do this:
                  Subject
                  Body of task"))
              2 (do (g/send-mail e-mail recepient subject body secret)
                    (t/send-text token id (str "E-mail was sent to " recepient "Subject: " subject "Body: " body ".") ))
             (do  (g/send-mail e-mail recepient subject (apply str (rest split))  secret )
                  (t/send-text token id (str "E-mail was sent to " recepient "Subject: " subject "Body: " body ".") )))
            ))))))

(defn -main
  [& args]
  (when (str/blank? token)
    (println "Please provde token in TELEGRAM_TOKEN environment variable!")
    (System/exit 1))

  (println "Starting the trellotaskbot")
  (<!! (p/start token handler)))

(def wow (p/start token handler))
(clojure.core.async/close! wow)
