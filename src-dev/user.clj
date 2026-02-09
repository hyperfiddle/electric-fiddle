(ns user) ; Under :dev alias, automatically load 'dev so the REPL is ready to go with zero interaction

(print "[user] loading dev... ") (flush)
(require 'dev) ; jetty 10+ – the default
;; (require '[dev-jetty9 :as dev]) ; require :jetty9 alias
(println "Ready.")