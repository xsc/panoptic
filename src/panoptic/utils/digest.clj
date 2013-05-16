(ns ^{:doc "Digest Creation for Panoptic"
       :author "Yannick Scherer"}
  panoptic.utils.digest
  (:import [java.security MessageDigest DigestInputStream]
           [java.io InputStream FileInputStream File]))

;; ## Conversion

(def ^:const ^:private ^String hex-chars "0123456789abcdef")

(defn- bytes-to-hex-string
  "Convert Byte Array to Hex String"
  ^String
  [^"[B" data]
  (let [^StringBuilder sb (StringBuilder. (* 2 (count data)))]
    (doseq [b data]
      (doto sb
        (.append (.charAt hex-chars (bit-shift-right (bit-and b 0xF0) 4)))
        (.append (.charAt hex-chars (bit-and b 0x0F)))))
    (.toString sb)))

;; ## Digest Base

(defn- create-message-digest
  "Create MessageDigest instance using the given Algorithm."
  ^MessageDigest
  [^String algorithm]
  (MessageDigest/getInstance algorithm))

;; ## Digest Creation

(defprotocol Hashable
  "Protocol for Entities a Digest can be derived from."
  (digest [this algorithm]
    "Compute Digest from the given Entity using the given Algorithm."))

(extend-protocol Hashable

  (class (byte-array 0))
  (digest [this algorithm]
    (let [md (create-message-digest algorithm)]
      (bytes-to-hex-string (.digest md this))))

  String
  (digest [this algorithm]
    (let [^"[B" data (.getBytes this)]
      (digest data algorithm)))

  InputStream
  (digest [this algorithm]
    (let [md (create-message-digest algorithm)]
      (with-open [^InputStream ds (DigestInputStream. this md)]
        (let [^"[B" buffer (byte-array 2048)]
          (while (not (= (.read ds buffer 0 2048) -1)) nil)))
      (bytes-to-hex-string (.digest md))))

  File
  (digest [this algorithm]
    (with-open [in (FileInputStream. this)]
      (digest in algorithm)))
  
  nil
  (digest [this algorithm]
    nil))

;; ## Shorthands

(def md2 
  "Create MD2 Hash of the given Entity."
  #(digest % "MD2"))

(def md5
  "Create MD5 Hash of the given Entity."
  #(digest % "MD5"))

(def sha1
  "Create SHA-1 Hash of the given Entity."
  #(digest % "SHA-1"))

(def sha256
  "Create SHA-256 Hash of the given Entity."
  #(digest % "SHA-256"))

(def sha384
  "Create SHA-384 Hash of the given Entity."
  #(digest % "SHA-384"))

(def sha512
  "Create SHA-512 Hash of the given Entity."
  #(digest % "SHA-512"))

(def file-md2
  "Create MD2 Hash of the File at the given Path."
  #(md2 (File. ^String %)))

(def file-md5
  "Create MD5 Hash of the File at the given Path."
  #(md5 (File. ^String %)))

(def file-sha1 
  "Create SHA-1 Hash of the File at the given Path."
  #(sha1 (File. ^String %)))

(def file-sha256 
  "Create SHA-256 Hash of the File at the given Path."
  #(sha256 (File. ^String %)))

(def file-sha384 
  "Create SHA-384 Hash of the File at the given Path."
  #(sha384 (File. ^String %)))

(def file-sha512
  "Create SHA-512 Hash of the File at the given Path."
  #(sha512 (File. ^String %)))
