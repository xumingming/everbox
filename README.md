# everbox

```clojure
(def everbox (combine evernote dropbox))
```

Evernote is a very useful, handy note taking software. And it also has many
wonderful GUI clients, you make some change, it auto-sync to the evernote server.
But as a software engineer, we used to editing files using Emacs/VIM rather than
using GUI clients, so `everbox` let you edit the files directly using your
favorite editor(Emacs!!), and it also auto-sync your changes to evernote server
(Just like dropbox).

It is still under developing.

## Usage

```bash
git checkout git@github.com:xumingming/everbox.git
cd everbox
lein run
```
## FAQ
### Why there is a folder named maven_repo?
  http://www.pgrs.net/2011/10/30/using-local-jars-with-leiningen/

### Why not using the standard version of thrift?
   http://discussion.evernote.com/topic/27805-evernote-libthrift

## License

Copyright © 2012 xumingming

Distributed under the Eclipse Public License, the same as Clojure.
