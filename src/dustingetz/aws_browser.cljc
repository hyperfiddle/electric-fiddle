(ns dustingetz.aws-browser
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            #?(:clj [cognitect.aws.client.api :as aws])
            [contrib.data :refer [index-by clamp-left]]
            [contrib.str :refer [pprint-str]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as router]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]
            [hyperfiddle.electric-forms0 :as forms :refer [Service]]
            [dustingetz.flatten-document :refer [flatten-nested]]))

#_(ns-unmap *ns* 'flatten-nested)

(e/defn Date_ [x] (dom/text (e/client (.toLocaleDateString x))))
(e/defn String_ [x] (dom/text x))
(e/defn Edn [x] (dom/text (pr-str x)))

#_(e/defn Typed-render [x]
  (let [r {inst? Date_
           string? String_}]
    (e/call (get r (type x)) x)))

(e/defn EntityRow [{:keys [tab name value]}]
  (dom/td (dom/text name) (dom/props {:style {:padding-left (some-> tab (* 15) (str "px"))}}))
  (dom/td (dom/text value)))

(e/defn TableScroll [xs! Row]
  (e/server
    (dom/props {:class "Viewport"})
    (let [record-count (count xs!)
          row-height 24
          [offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor 1})]
      (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
        (e/for [i (IndexRing limit offset)]
          (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
            (Row #_(datafy) #_(nav xs! i) (nth xs! i nil)))))
      (dom/div (dom/props {:style {:height (str (clamp-left ; row count can exceed record count
                                                  (* row-height (- record-count limit)) 0) "px")}})))))

(e/defn OpsRow [{:keys [name documentation] :as m}]
  (dom/td (router/link ['. [(keyword name)]] (dom/text name)))
  (dom/td (dom/text documentation)))

(e/defn DebugRow [?m] (dom/td (dom/text (some-> ?m pr-str))))

(e/defn AutoRow [?m]
  (e/server
    (e/for [k (e/diff-by {} (some-> ?m keys))]
      (dom/td (dom/text (Edn (some-> ?m k)))))))

(e/defn Edn-document [x]
  (e/server
    (let [m (datafy x)]
      (dom/fieldset (dom/legend (dom/text "(aws/client {:api :s3 :region \"us-east-1\"})"))
        (dom/div (TableScroll
                   (vec (flatten-nested m))
                   EntityRow))))))

(e/defn FormCollection [title m Row
                        title2 xs Row2]
  ; collect a route from the top thing
  (dom/fieldset (dom/props {:class "title-record"})
    (dom/legend (dom/text title))
    (dom/div (TableScroll (vec (flatten-nested m)) Row)))
  (dom/fieldset (dom/props {:class "collection"})
    (dom/legend (dom/text title2))
    (dom/div (TableScroll xs Row2))))

(e/defn S3-Index [x]
  (e/server
    (let [m (e/server (datafy x))]
      (FormCollection
        "AWS S3 Service Index" (dissoc m :ops) EntityRow
        "Ops" (vec (sort-by :name (vals (:ops m)))) OpsRow))))

(def config ; map of op to dominant collection attr
  {:Index :ops #_(fn [m] (vec (sort-by :name (vals (:ops m)))))
   :ListBuckets :Buckets})

(e/defn S3 []
  (let [x (e/server (aws/client {:api :s3 :region "us-east-1"}))
        [op] router/route]
    (if-not op
      (router/ReplaceState! ['. [:Index]])
      (e/server
        (case op
          :Index (S3-Index x) ; custom query
          (let [m (datafy (aws/invoke x {:op op}))
                k (config op)]
            (FormCollection
              op (dissoc m k) EntityRow
              k (nav m k (k m)) AutoRow)))))))

(e/defn Page []
  (e/client
    (dom/div (dom/text "Nav: ")
      (router/link ['. [:Index]] (dom/text "index")) (dom/text " "))
    (let [[page] router/route]
      #_(dom/h1 (dom/text page " — Data Browser"))
      (router/pop
        (binding [forms/effects* {}]
          (Service
            (case page
              :S3 (S3)
              :Edn (Edn-document (e/server (aws/client {:api :s3 :region "us-east-1"})))
              (e/amb))))))))

(declare css)
(e/defn DataBrowser []
  (e/client
    (dom/style (dom/text css))
    (let [[page] router/route]
      (dom/div (dom/props {:class (str "DirectoryExplorer " (some-> page name))})
        (if-not page
          (router/ReplaceState! ['. [:S3]])
          (Page))))))

(comment
  (def s3 (aws/client {:api :s3 :region "us-east-1"}))
  (vec (datafy s3))

  (flatten-nested (datafy s3))

  #_(aws/validate-requests s3 true) ; broken
  (def x (aws/invoke s3 {:op :ListBuckets}))
  (meta x)
  (as-> x x
    (nav x :Buckets (:Buckets x)))

  (datafy (git/load-repo "./"))
  )

(def css "
/* Scroll machinery */
.DirectoryExplorer .Viewport { overflow-x:hidden; overflow-y:auto; }
.DirectoryExplorer table { display: grid; }
.DirectoryExplorer table tr { display: contents; visibility: var(--visibility); }
.DirectoryExplorer table td { grid-row: var(--order); }

/* fullscreen, except in tutorial mode */
.DirectoryExplorer fieldset.title-record { position:fixed; top:0em; bottom:50vh; left:0; right:0; }
.DirectoryExplorer fieldset.collection { position:fixed; top:50vh; bottom:0; left:0; right:0; }
.DirectoryExplorer div.Viewport { height: 100%; }

/* Cosmetic styles */
/* .DirectoryExplorer table { grid-template-columns: 15em auto; } */

.DirectoryExplorer fieldset { padding: 0; padding-left: 0.5em; background-color: white; }
.DirectoryExplorer legend { margin-left: 1em; font-size: larger; }
.DirectoryExplorer table td { height: 24px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.DirectoryExplorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; }
.DirectoryExplorer table tr:hover td { background-color: #ddd; }

.DirectoryExplorer.Edn fieldset { position:fixed; top:0em; bottom:0vh; left:0; right:0; }

")
