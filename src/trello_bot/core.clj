(ns trellotaskbot.core
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [morse.handlers :as h]
            [morse.polling :as p]
            [morse.api :as t]
            [cheshire.core :refer :all :as json]
            [send-mail :as g ]
            [clj-http.client :as http]
            [clojure.java.io :as io])
  (:gen-class))

(def token (env :telegram-token) )
(def db (atom {}))
(def db-file (env :file-db))
(def e-mail  (env :trello-mail))
(def secret  (env :mail-password))
(def base-url "https://api.telegram.org/bot")

(when
  (not (empty? (slurp db-file)))
  (reset! db (read-string (slurp db-file))))

(defn e-mail? [text]
  (str/ends-with? text "@boards.trello.com" ))

(defn get-old-user-task [id new-or-old text]
  (let [ split (str/split text #"\n")
        subject (first split)]
    (if (= 1 (count split))
      (t/send-text token id "It's not a valid task message. Do this:
                  Subject
                  Body of task")
      (do  (g/send-mail e-mail new-or-old subject (apply str (rest split))  secret )
           (t/send-text token id (str "E-mail was sent to " new-or-old "Subject: " subject " Body: " (rest split) "."))))))

(defn get-new-user-mail [text id]
  (if (e-mail? text)
    (do (swap! db assoc (str id) text )
        (t/send-text token id (str "This e-mail: " text " will be used as for sending tasks to Trello."))
        (spit db-file @db))
    (t/send-text token id "It's not a valid Trello e-mail address. Use /help to get one.")))

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
      (if-let [got-it (get @db (str id))]
        (t/send-text token id (str "Hello, " name "!" " Welcome to TrelloTaskBot. Your e-mail is:" got-it "To change e-mail use /forgetme to delete your e-mail and then enter new e-mail."
                                 ))
        (t/send-text token id (str "Hello, " name "! " "Welcome to TrelloTaskBot. You're new user, so we need your Trello board e-mail." )))))

  (h/command-fn "help"
    (fn [{{id :id :as chat} :chat}]
      (println "Help was requested in " chat)
      (t/send-text token id
                  {:reply_markup {:inline_keyboard
                                  [[{:text "How to create Trello board e-mail (official docs)."
                                     :url "https://help.trello.com/article/809-creating-cards-by-email"
                                     }]]}}
                   "To create Trello e-mail use the link below. \n To create task in TrelloBot:\n Taskname \n Then enter task body using newline."
                   )))

  (h/command-fn "forgetme"
      (fn [{{id :id}  :chat :as message}]
      (do (t/send-photo token id (io/file (io/resource "a.png")))
          (swap! db dissoc (str id))
          (spit db-file @db))))

  (h/message-fn
    (fn [{{id :id}  :chat :as message}]
      (let [text (:text message)
            new-or-old  (get @db (str id))]
        (if (= text "/forgetme")
          (println "This user has been forgotten" (:username (:from message)))
          (do (println "Intercepted message: " message)
              (if new-or-old
                (get-old-user-task id new-or-old text)
                (get-new-user-mail text id))) )))))

(defn -main
  [& args]
  (when (str/blank? token)
    (println "Please provide token in TELEGRAM_TOKEN environment variable!")
    (System/exit 1))

  (println "Starting the trellotaskbot")
  (<!! (p/start token handler)))

(def wow (p/start token handler))
(clojure.core.async/close! wow)

