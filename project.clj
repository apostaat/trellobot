(defproject trello-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [environ             "1.1.0"]
                 [morse               "0.2.4"]
                 [com.draines/postal "2.0.3"]
                 [cheshire           "5.10.0"]
                 [send-mail           "0.1.0"]
                 [clj-http            "3.10.1"]]

  :plugins [[lein-environ "1.1.0"]]
  :main ^:skip-aot trellotaskbot.core
  :target-path "target/%s"

  :profiles {:dev {:env {:telegram-token ""
                         :trello-mail ""
                         :mail-server ""
                         :mail-user ""
                         :mail-password ""}}
             :uberjar {:aot :all}})
