(ns inferenceql.publish.asciidoc
  (:import [java.util HashMap]
           [org.asciidoctor Asciidoctor$Factory]
           [org.asciidoctor Options]
           [org.asciidoctor SafeMode]
           [org.asciidoctor.ast ContentModel]
           [org.asciidoctor.ast Document]
           [org.asciidoctor.extension BlockProcessor]
           [org.asciidoctor.extension DocinfoProcessor]
           [org.asciidoctor.extension LocationType]
           [org.asciidoctor.extension Contexts])
  (:require [com.stuartsierra.component :as component]))

(defn docinfo-processor
  [f & {:keys [location]}]
  {:pre [(some? f)
         (some? location)
         (contains? #{LocationType/HEADER LocationType/FOOTER} location)]}
  (let [config (doto (HashMap.)
                 (.put "location" (.optionValue location)))]
    (proxy [DocinfoProcessor] [config]
      (process [^Document document]
        (f this document)))))

(defn add-stylesheet-processor
  [url]
  (docinfo-processor
   (fn [_ document]
     (when (.isBasebackend document "html")
       (str "<link rel=\"stylesheet\" href=\"" url "\"></link>")))
   :location LocationType/HEADER))

(defn add-script-processor
  [url]
  (docinfo-processor
   (fn [_ document]
     (when (.isBasebackend document "html")
       (str "<script type=\"text/javascript\" crossorigin src=\"" url "\"></script>")))
   :location LocationType/HEADER))

(defn block-processor
  [& {:keys [process-fn name contexts content-model]}]
  {:pre [(some? process-fn) (some? name)]}
  (let [config (HashMap.)]
    (when contexts (.put config Contexts/KEY contexts))
    (when content-model (.put config ContentModel/KEY content-model))
    (proxy [BlockProcessor] [name config]
      (process [parent reader attributes]
        (process-fn this parent reader attributes)))))

(def yell-block-processor
  (block-processor :process-fn (fn [this parent _reader attributes]
                                 (.createBlock this parent "pass" "It worked!" attributes))
                   :name "yell"
                   :contexts [Contexts/PARAGRAPH Contexts/LISTING]
                   :content-model ContentModel/SIMPLE))

(def iql-block-processor
  (block-processor
   :name "iql"
   :contexts [Contexts/PARAGRAPH Contexts/LISTING Contexts/EXAMPLE]
   :content-model ContentModel/SIMPLE
   :process-fn (fn [this parent reader _attributes]
                 (let [id (gensym)]
                   (.append parent (.createBlock this parent "pass" (str "<div id=\"" id "\"/>")))
                   ;; (.append parent (.createBlock this parent "pass" "<script crossorigin src=\"https://unpkg.com/react@18/umd/react.development.js\"></script>"))
                   ;; (.append parent (.createBlock this parent "pass" "<script crossorigin src=\"https://unpkg.com/react-dom@18/umd/react-dom.development.js\"></script>"))
                   ;; (.append parent (.createBlock this parent "pass" "<script type=\"text/javascript\">console.log(\"Hello, world!\");</script>"))
                   )
                 parent)))

(def ^:private asciidoctor
  (let [asciidoctor (Asciidoctor$Factory/create)]
    (let [extension-registry (.javaExtensionRegistry asciidoctor)]
      (.docinfoProcessor extension-registry (add-stylesheet-processor "styles/github.css"))
      (.docinfoProcessor extension-registry (add-script-processor "js/main.js"))
      (.block extension-registry yell-block-processor)
      (.block extension-registry iql-block-processor))
    asciidoctor))

(defrecord Asciidoctor []
  component/Lifecycle

  (start [component]
    (let [asciidoctor (Asciidoctor$Factory/create)]
      (assoc component :asciidoctor asciidoctor)))

  (stop [{:keys [asciidoctor]}]
    (.shutdown asciidoctor)))

(defn title
  [f]
  (-> (.load asciidoctor (slurp f) {})
      (.doctitle)))

(defn ->html
  "Transforms an Asciidoc string into an HTML string."
  [s]
  (let [options (-> (Options/builder)
                    (.headerFooter true)
                    (.attributes {"linkcss" false})
                    (.safe SafeMode/SECURE)
                    (.build))]
    (.convert asciidoctor s options)))
