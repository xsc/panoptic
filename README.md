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

__REPL__

```clojure
(use 'panoptic.core)

(def watcher
  (-> (simple-file-watcher ["error.txt" "test.txt"])
    (on-file-modify #(println (:path %) "changed"))
    (on-file-create #(println (:path %) "created"))
    (on-file-delete #(println (:path %) "deleted"))
    (start-watcher!)))

...

@(stop-watcher! watcher)
```

## License

Copyright &copy; 2013 Yannick Scherer

Distributed under the Eclipse Public License, the same as Clojure.
