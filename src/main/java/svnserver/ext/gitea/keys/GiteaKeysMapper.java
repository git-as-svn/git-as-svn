/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.keys;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gitea.ApiClient;
import io.gitea.ApiException;
import io.gitea.api.UserApi;
import io.gitea.model.User;
import io.gitea.model.UserSearchList;
import svnserver.context.Shared;
import svnserver.ext.gitea.config.GiteaContext;
import svnserver.ext.keys.KeysMapper;

/**
 * KeysMapper.
 *
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
public class GiteaKeysMapper extends Thread implements Shared, KeysMapper {

  private static final int RUN_DELAY = 15 * 1000;
  private static final int INITIAL_DELAY = 1000;

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GiteaKeysMapper.class);

  @NotNull
  private ConcurrentHashMap<String, String> keyMap = new ConcurrentHashMap<>();
  @NotNull
  private ConcurrentHashMap<String, String> blacklisted = new ConcurrentHashMap<>();
  @NotNull
  private HashSet<String> toAdd = new HashSet<>();
  @NotNull
  private GiteaContext giteaContext;

  public GiteaKeysMapper(GiteaContext giteaContext) {
    this.giteaContext = giteaContext;
    this.start();
  }

  @Override
  public void setKeys(@NotNull HashSet<String> keysSet) {
    keysSet.removeAll(keyMap.keySet());
    keysSet.removeAll(blacklisted.keySet());

    synchronized (toAdd) {
      toAdd.addAll(keysSet);
    }
  }

  @Nullable
  public String getUserName(@NotNull String keyId) {
    return keyMap.get(keyId);
  }

  public void run() {
    ApiClient apiClient = giteaContext.connect();
    final UserApi userApi = new UserApi(apiClient);
    final HashMap<String, Integer> internalToAdd = new HashMap<>();
    try {
      while (!isInterrupted()) {
        synchronized (toAdd) {
          if (toAdd.size() > 0) {
            for (String keyId : toAdd) {
              internalToAdd.put(keyId,
                                internalToAdd.getOrDefault(keyId, 0));
            }
            toAdd.clear();
          }
        }
        sleep(INITIAL_DELAY);
        if (internalToAdd.size() > 0 && !isInterrupted()) {
          log.info("Need to add {} keys. Looking up users...", internalToAdd.size());
          try {
            HashMap<String, String> keyMapReplacement = new HashMap<>();
            UserSearchList userSearchList = userApi.userSearch(null, null, null);
            List<User> users = userSearchList.getData();
            log.info("Found {} users", users.size());
            // That may take some time so check if we've been interrupted
            // in the intervening time
            if (isInterrupted()) {
              break;
            }

            users.parallelStream().forEach(user -> {
              try {
                String username = user.getLogin();

                userApi.userListKeys(username, null).parallelStream().forEach(key -> {
                  keyMapReplacement.put("key-" + key.getId().toString(), username);
                });
              } catch (ApiException e) {
                throw new RuntimeException(e);
              }
            });
            keyMap.clear();
            keyMap.putAll(keyMapReplacement);
          } catch (ApiException e) {
            throw new RuntimeException(e);
          }
          for (Iterator<String> it = internalToAdd.keySet().iterator(); it.hasNext();) {
            String keyId = it.next();
            if (keyMap.containsKey(keyId)) {
              it.remove();
            } else if (internalToAdd.get(keyId) > 3) {
              log.info("Blacklisting key: {}", keyId);
              blacklisted.put(keyId, keyId);
              it.remove();
            } else {
              internalToAdd.put(keyId, internalToAdd.get(keyId) + 1);
            }
          }
          if (internalToAdd.size() > 0) {
            log.info("Still missing {} keys. Have found {} keys, and blacklisted {} keys",  internalToAdd.size(), keyMap.size(), blacklisted.size());
          }
        }
        sleep(RUN_DELAY);
      }
    } catch (InterruptedException e) {
      return;
    }
  }

  public void close() {
    this.interrupt();
  }
}