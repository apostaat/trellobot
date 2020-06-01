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

(defn e-mail? [text]
  (str/ends-with? text "@boards.trello.com" ))

(defn get-mail-from-db [id] (get @db (str id)))

(defn save [id text]
  (swap! db assoc (str id) text )
  (spit db-file @db))

(defn delete [id]
  (swap! db dissoc (str id))
  (spit db-file @db))

(defn my-mail-send
  [user-mail subject details id]
                   (do  (g/send-mail e-mail user-mail subject
                                     (apply str details)
                                     secret )
                        (t/send-text token id
                                     (str "E-mail was sent to " user-mail
                                          " \nSubject: " subject
                                          " \nBody: " details ))))


(defn create-old-user-task [id user-mail text]
  (let [ [subject & details] (str/split-lines text) ]
    (if (nil? details)
      (my-mail-send user-mail subject " " id)
      (my-mail-send user-mail subject (str/join "\n" details) id))))

(defn save-new-user-mail [text id]
  (if (e-mail? text)
    (do (save id text)
        (t/send-text token id (str "This e-mail: " text " will be used as for sending tasks to Trello.")))
    (t/send-text token id "It's not a valid Trello e-mail address. Use /help to get one.")))


(h/defhandler handler
  (h/command-fn "start"
    (fn [{{id :id name :first_name :as chat} :chat}]
      (if (get-mail-from-db id )
        (t/send-text token id
                     (str "Hello, " name "!" " Welcome to TrelloTaskBot. Your e-mail is:" (get-mail-from-db id ) "To change e-mail use /forgetme to delete your e-mail and then enter new e-mail."))
        (t/send-text token id (str "Hello, " name "! " "Welcome to TrelloTaskBot. You're new user, so we need your Trello board e-mail." )))))

  (h/command-fn "help"
    (fn [{{id :id :as chat} :chat}]
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
          (delete id))))

  (h/message-fn
    (fn [{{id :id}  :chat :as message}]
      (let [text (:text message)]
        (when (not (= text "/forgetme"))
          (do   (if (get-mail-from-db id )
                (create-old-user-task id (get-mail-from-db id ) text)
                (save-new-user-mail text id))))))))

(defn -main
  [& args]
  (when (str/blank? token)
    (println "Please provide token in TELEGRAM_TOKEN environment variable!")
    (System/exit 1))
  (let [slurp-file (slurp db-file)]
    (when
      (not (empty? slurp-file))
          (reset! db (read-string slurp-file))))
  (<!! (p/start token handler)))


