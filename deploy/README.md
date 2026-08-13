# Timer web deployment

The installable web timer is a static site served from
`minionsofdarkness.com/timer/`. It does not run an application service and does
not proxy any request to the Minions Python process.

Production uses immutable, commit-addressed releases:

```text
/opt/minions-timer/
  current -> releases/<full-git-commit>
  releases/<full-git-commit>/web/
```

Release directories and files are owned by `root:root`, directories are mode
`0755`, and files are mode `0644`. The nginx master process may start as root
to bind ports 80 and 443, but static requests are handled by its unprivileged
`nginx` workers. No timer-specific process runs as root (or at all).

`nginx-minions-timer.conf` defines a separate static location and document
root. It permits only `GET`/`HEAD` and disables directory indexes. The parent
virtual host supplies the restrictive security headers. Because the alias ends
at the release's `web` directory and contains no dot-files, the
timer cannot address `/opt/minions`, its SQLite data, or the game service on
port 8000.

Deploy by archiving a verified commit into a new release directory, applying
the ownership and modes above, validating the tree and nginx configuration,
then atomically switching `current`. Keep the previous symlink target for
rollback. Increment `CACHE_NAME` in `web/service-worker.js` whenever cached
application-shell files change.
