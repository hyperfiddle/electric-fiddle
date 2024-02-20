(ns datagrid.writer
  (:require [clojure.java.shell :as shell]))

(defn copy-command [source-path destination-path] (str "cat " source-path " | sudo dd of=" destination-path))
(defn sudo-command [command] (str "/usr/bin/osascript -e 'do shell script \"" command " 2>&1 \" with administrator privileges'"))
(defn run-command! [command] (shell/sh "bash" "-c" command))

(defn write-hosts-file! [content-str]
  (let [source-path "/tmp/electric_hosts_editor.hosts"
        target-path "/etc/hosts"
        command (sudo-command (copy-command source-path target-path))]
    (spit source-path content-str)
    (println "Will run command with sudo rights: " command)
    (run-command! command)))

