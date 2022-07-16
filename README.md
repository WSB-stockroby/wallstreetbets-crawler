# wallstreetbets-crawler

You will need to `git-crypt unlock` the repo first.

```
TAG=TEST; \
just docker-build $TAG; \
docker run -i --rm wallstreetbets-crawler:$TAG > output
```
# TODOs
- [ ] `docker build` should not depend on the project's `deps.edn` file (add git URLs instead of local alieses)
- [ ] why don't we package the secret file into the JAR? (it is probably easier to swap it out this way - e.g.: with configmaps on kubernetes) 
