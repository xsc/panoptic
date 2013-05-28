(ns ^ {:doc "Clipboard Utilities"
       :author "Yannick Scherer"}
  panoptic.utils.clipboard
  (:import [java.awt Toolkit Image]
           [java.awt.image DataBufferByte]
           [java.awt.datatransfer Clipboard DataFlavor StringSelection]))

(def ^:dynamic *clipboard* 
  "The clipboard to operate on."
  (try
    (let [t (Toolkit/getDefaultToolkit)]
      (.getSystemClipboard t))
    (catch Exception _ nil)))

(defn- flavor-available?
  "Check if DataFlavor is available."
  [^Clipboard clipboard ^DataFlavor flavor]
  (when (and clipboard flavor)
    (.isDataFlavorAvailable clipboard flavor)))

(defn contents
  "Get Clipboard Contents if they match the given flavor (string by default)."
  ([] (contents *clipboard* DataFlavor/stringFlavor))
  ([^Clipboard clipboard] 
   (contents clipboard DataFlavor/stringFlavor))
  ([^Clipboard clipboard ^DataFlavor flavor]
   (try
     (when (flavor-available? clipboard flavor)
       (when-let [c (.getContents clipboard nil)]
         (when (.isDataFlavorSupported c flavor)
           (.getTransferData c flavor))))
     (catch Exception _ nil))))

(defn contains-string?
  "Check if the Clipboard contains a String."
  ([] (contains-string? *clipboard*))
  ([^Clipboard clipboard] (flavor-available? clipboard DataFlavor/stringFlavor)))

(defn contains-image?
  "Check if the Clipboard contains an Image."
  ([] (contains-image? *clipboard*))
  ([^Clipboard clipboard] (flavor-available? clipboard DataFlavor/imageFlavor)))

(defn contains-file-list?
  "Check if the Clipboard contains a File List."
  ([] (contains-file-list? *clipboard*))
  ([^Clipboard clipboard] (flavor-available? clipboard DataFlavor/javaFileListFlavor)))

(defn string-contents
  "Get Clipboard contents as String."
  (^String [] (contents))
  (^String [clipboard] (contents clipboard)))

(defn image-contents
  "Get Clipboard contents as java.awt.Image."
  (^Image [] (contents *clipboard* DataFlavor/imageFlavor))
  (^Image [clipboard] (contents clipboard DataFlavor/imageFlavor)))

(defn file-list-contents
  "Get Clipboard contents as a seq of File objects."
  ([] (seq (contents *clipboard* DataFlavor/javaFileListFlavor)))
  ([clipboard] (seq (contents clipboard DataFlavor/javaFileListFlavor))))

(defn byte-contents
  "Get contents of clipboard as byte array (either the UTF-8 bytes of a string
   or the bytes of an image buffer)."
  ([] (byte-contents *clipboard*))
  ([^Clipboard clipboard]
   (if-let [s (string-contents clipboard)]
     (.getBytes s "UTF-8")
     (if-let [img (image-contents clipboard)]
       (let [^DataBufferByte buf (.getDataBuffer (.getData img))]
         (.getData buf))
       nil))))
