(ns docs-site.blog.threaddump3
  (:require [contrib.data :refer [unqualify]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :refer [Tap]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Checkbox]]
            [hyperfiddle.router3 :as r]
            [dustingetz.entity-browser0 :refer [EntityBrowser0]]
            #?(:clj dustingetz.datafy-git)
            #?(:clj dustingetz.datafy-jvm)))

(e/defn UserResolve [[tag id]]
  (case tag
    ::tap (Tap)
    ::thread-mx (e/server (dustingetz.datafy-jvm/resolve-thread-manager))
    ::thread (e/server (dustingetz.datafy-jvm/resolve-thread id))
    ::git (e/server (dustingetz.datafy-git/load-repo id))
    (e/amb)))

(declare css)
(e/defn ThreadDump3 []
  (e/client (dom/style (dom/text css))
    (dom/text "Target: ")
    (e/for [[tag e :as ref] (e/amb [::thread-mx] [::thread 1] [::git "./"] [::tap])]
      (r/link ['. [ref]] (dom/text (pr-str (remove nil? [(unqualify tag) e])))))

    (binding [dustingetz.entity-browser0/Resolve UserResolve]
      (r/Apply EntityBrowser0 [[::git "./"]]))))

(def css "
.Tutorial a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */
")

; Entity browser - shallow objects
; attach to URI not object
; links need to identify targets, otherwise use a treeview to unfold paths
; auto-render routable (identified) objects as links, from their identity column
; inline components render as treeview
; given a companion meta object, interpret methods as inline objects


; top view: the document
;   routable objects as hyperlinks
;   inline components as treeview folders
;   select any document attribute to see details below
; bottom view: the selection


; select a row in the top to target the bottom, and vice versa
; breadcrumbs in both legends
; target object above, there's a 3rd outer fieldset/legend
; selection works on any row

; No, it's a stack - N grids
; top view - identified target document
; second view - selected top record - path in focused route
; nth view - selected n-1 record - path in focused route
; now, nesting is routable!

; methods are hyperlinks? Ok but are they routable?
; yes only via breadcrumbs
; path segments are typed (by spec?), so if we route a partial fn, just call it (and render a form!)

; breadcrumb nav is hyperlink, like ...
; self-links route
; if you wanna route a thing top level (clearing breadcrumbs) - click a self link
; are they a different color?
; collections do not have self links, their parent object does - they are methods on parent objects
; if we recognize an object's type as identifiable, then we can render a self link. In the left-most column?
; in object view: self link is the object's identity attribute, i.e. `{'identify (identify o)}