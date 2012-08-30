(defproject everbox "0.1"
  :description "FIXME: write description"
  :url "https://github.com/xumingming/everbox"
  :resources-path "conf"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [evernote/evernote-api "1.22"]
                 [evernote/thrift "1.0"]
                 [clj-yaml "0.4.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [log4j/log4j "1.2.16"]]
  :repositories {"local" ~(str (.toURI (java.io.File. "maven_repo")))}
  :warn-on-reflection true
  :main everbox.core)
