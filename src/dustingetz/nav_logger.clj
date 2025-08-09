(ns dustingetz.nav-logger
  (:require [hyperfiddle.hfql0 :as hfql]))

; how to publish a reactive collection to the virtual scroll?
; the virtual scroll controls the viewport, it expects to sample over an array
; how to subscribe to changes to cells in the array that the view is subscribed to?
; probably m/reductions, so we return a missionary signal here for IndexRing to sample element-wise
; let's start with a reactive datomic query first

