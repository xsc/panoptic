# panoptic

__panoptic__ monitors single files or whole directories for changes.

[![Build Status](https://travis-ci.org/xsc/panoptic.png)](https://travis-ci.org/xsc/panoptic)
[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

This is only for playing around for now. But much more functionality can be expected.

## Usage

__Leiningen__ ([via Clojars](https://clojars.org/panoptic))

```clojure
[panoptic "0.3.0"]
```

__REPL__

```clojure
(use 'panoptic.core)
```

## Observables

- file creation, deletion and modification
- directory creation and deletion
- child directory creation and deletion
- child file creation and deletion
- creation and deletion of all nodes in a directory hierarchy

## Quick Examples

__Watching Files__

```clojure

(def w 
  (-> (file-watcher :checksum :crc32)
    (on-file-modify #(println (:path %3) "changed"))
    (on-file-create #(println (:path %3) "created"))
    (on-file-delete #(println (:path %3) "deleted"))))
(run-blocking! w ["error.log" "access.log"])
```

__Watching Directories__

```clojure
(def w
  (-> (directory-watcher :recursive true :extensions [:clj])
    (on-directory-create #(println "Directory" (:path %3) "created"))
    (on-directory-delete #(println "Directory" (:path %3) "deleted"))
    (on-file-create #(println "File" (:path %3) "created"))
    (on-file-delete #(println "File" (:path %3) "deleted"))))
(run-blocking! w ["/var/log/my-logs"] :threads 4)
```

## Running Examples

To run an example (see "examples" directory) issue the following command:

```
lein run-example <Example> <Parameters>
```

## License

Copyright &copy; 2013 Yannick Scherer

Distributed under the Eclipse Public License, the same as Clojure.
