# panoptic

__panoptic__ monitors single files or whole directories for changes.

[![Build Status](https://travis-ci.org/xsc/panoptic.png)](https://travis-ci.org/xsc/panoptic)
[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

This is only for playing around for now. But much more functionality can be expected.

## Usage

__Leiningen__ ([via Clojars](https://clojars.org/panoptic))

```clojure
[panoptic "0.1.0-SNAPSHOT"]
```

__Watching (possibly non-existing) Files__

```clojure
(use 'panoptic.core)

(def watcher
  (-> (file-watcher :checker md5)
    (on-file-modify #(println (:path %3) "changed"))
    (on-file-create #(println (:path %3) "created"))
    (on-file-delete #(println (:path %3) "deleted"))
    (start-simple-watcher! ["log.txt" "error.txt"] :interval 500)))

...

@(stop-watcher! watcher)
```

__Watching Directories__

```clojure
(use 'panoptic.core)

(def watcher
  (-> (directory-watcher :recursive true :extensions [:clj])
    (on-directory-create #(println "Directory" (:path %3) "created"))
    (on-directory-delete #(println "Directory" (:path %3) "deleted"))
    (on-directory-file-create #(println "File" (:path %3) "created"))
    (on-directory-file-delete #(println "File" (:path %3) "deleted"))
    (start-simple-watcher! ["/path/to/directory"] :interval 500)))

...

@(stop-watcher! watcher)
```

## Running Examples

To run an example (see "examples" directory) issue the following command:

```
lein run-example <Example> <Parameters>
```

## License

Copyright &copy; 2013 Yannick Scherer

Distributed under the Eclipse Public License, the same as Clojure.
