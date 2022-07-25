(ns inferenceql.notebook.asciidoc
  (:import [org.asciidoctor Asciidoctor$Factory]
           [org.jsoup Jsoup])
  (:require [com.stuartsierra.component :as component]))

(defonce ^:private asciidoctor
  (Asciidoctor$Factory/create))

(defrecord Asciidoctor []
  component/Lifecycle

  (start [component]
    (let [asciidoctor (Asciidoctor$Factory/create)]
      (assoc component :asciidoctor asciidoctor)))

  (stop [{:keys [asciidoctor]}]
    (.shutdown asciidoctor)))

(defn ->html
  "Transforms an Asciidoc string into an HTML string."
  [s]
  (let [html (.convert asciidoctor s {"header_footer" true
                                      "attributes" {"linkcss" false}})
        doc (Jsoup/parse html)]
    (-> (.select doc "div#footer")
        (.remove))
    (-> (.select doc "link[href=\"./asciidoctor.css\"]")
        (.remove))
    (doseq [selector ["pre" "code"]]
      (doseq [parent (.select doc selector)]
        (doseq [element (.getAllElements parent)]
          (-> element
              (.removeAttr "id")
              (.removeAttr "class")
              (.removeAttr "data-lang")))))
    (str doc)))
