package org.elasticsearch.plugin.elasticfence.data;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import com.google.common.collect.Lists;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.plugin.elasticfence.UserAuthenticator;
import org.elasticsearch.plugin.elasticfence.logger.EFLogger;
import org.elasticsearch.search.SearchHit;

import static org.elasticsearch.common.xcontent.XContentFactory.*;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
/**
 * A bridge class of UserData and Elasticsearch index data. 
 * @author tk
 */
public class UserDataBridge {
	private static final String HTTP_USER_AUTH_INDEX = ".http_user_auth";
	private static final String HTTP_USER_AUTH_TYPE = "user";
	private static boolean isInitialized = false;
	private Client client;

    //pass shard numbers here to preserve access level of settings
	public UserDataBridge(Client client, int number_of_shards, int number_of_replicas) {
		this.client = client;
		if (!isInitialized && !createIndexIfEmpty(number_of_shards, number_of_replicas)) {
			EFLogger.error("failed to create index: " + HTTP_USER_AUTH_INDEX);
		}
	}
	
	public boolean isInitialized() {
		if (!isInitialized) {
			reloadUserDataCache();
		}
		// check again after the initialization 
		if (isInitialized) {
			return true;
		}

		return false;
	}
	
	public String listUser () {
		String response = "[";
		List<UserData> allUserData = getAllUserData();
		for (UserData userData : allUserData) {
			response += userData.toJSON() + ",";
		}
		if (allUserData.size() > 0) 
			response = response.substring(0, response.length() - 1);
		response += "]";
		return response;
	}
	
	public boolean createUser (String userName, String password) {
		if (userName == null || userName.equals("")) {
			return false;
		}
		userName = userName.toLowerCase();
		if (userName.equals("root")) {
			return false;
		}
		
		UserData user = getUser(userName);
		if (user != null) {
			EFLogger.error("username " + userName + " is already registered");
			return false;
		} else {
			user = new UserData(userName, password);
			putUser(user);
			return true;
		}
	}
	
	/**
	 * add permission to an user with specified indices
	 * @param userName
	 * @param indexName
	 * @return
	 */
	public boolean addAuthIndex (String userName, String indexName, boolean readRight, boolean writeRight, boolean deleteRight) {
		if (userName == null || userName.equals("") || indexName == null || indexName.equals("")) {
			return false;
		}
		userName = userName.toLowerCase();
		indexName = indexName.toLowerCase();
		if (userName.equals("root")) {
			return false;
		}

		UserData user = getUser(userName);
		if (user == null) {
			return false;
		}

        HashMap<String, HashMap<String, Boolean>> indexFilters = user.getIndexFilters();
		String[] indexNames = indexName.split(",");

		for (String index : indexNames) {
			index = index.trim();

			if (index == null || index.equals("")) {
				continue;
			}
			if (index.charAt(0) != '/') {
				index = "/" + index;
			}
			if (index.equals("/*")) {
				continue;
			}
            indexFilters.put(index, new HashMap<String, Boolean>()
                {{
                    put("read", readRight);
                    put("write", writeRight);
                    put("delete", deleteRight);
                }}
            );
		}

		user.setFilters(indexFilters);
		return putUser(user);
	}
	
	/**
	 * update permission of an user with specified indices
	 * @param userName
	 * @param indexName
	 * @return
	 */
	public boolean updateAuthIndex (String userName, String indexName, boolean readRight, boolean writeRight, boolean deleteRight) {
		if (userName == null || userName.equals("") || indexName == null || indexName.equals("")) {
			return false;
		}
		userName = userName.toLowerCase();
		indexName = indexName.toLowerCase();
		if (userName.equals("root")) {
			return false;
		}

		UserData user = getUser(userName);
		if (user == null) {
			return false;
		}

        HashMap<String, HashMap<String, Boolean>> indexFilters = new HashMap<String, HashMap<String, Boolean>>();
		String[] indexNames = indexName.split(",");

		for (String index : indexNames) {
			index = index.trim();

			if (index == null || index.equals("")) {
				continue;
			}
			if (index.charAt(0) != '/') {
				index = "/" + index;
			}
			if (index.equals("/*")) {
				continue;
			}

            indexFilters.put(index, new HashMap<String, Boolean>()
                    {{
                        put("read", readRight);
                        put("write", writeRight);
                        put("delete", deleteRight);
                    }}
            );
		}
		user.setFilters(indexFilters);
		return putUser(user);
	}
	
	/**
	 * add permission to an user with a specified index
	 * @param userName
	 * @param password
	 * @param indexName
	 * @return
	 */
	public boolean removeAuth (String userName, String password, String indexName) {
//		if (userName == null || userName.equals("") || indexName == null || indexName.equals("")) {
//			return false;
//		}
//		userName = userName.toLowerCase();
//		indexName = indexName.toLowerCase();
//		if (userName.equals("root")) {
//			return false;
//		}
//
//		UserData user = getUser(userName);
//		if (user == null)
//			return false;
//		if (user.isValidPassword(password)) {
//			Set<String> indices = user.getIndexFilters();
//			if (indexName.charAt(0) != '/') {
//				indexName = "/" + indexName;
//			}
//			if (indices.contains(indexName)) {
//				indices.remove(indexName);
//				putUser(user);
//				return true;
//			}
//		}
		return false;
	}
	
	public boolean changePassword (String userName, String oldPassword, String newPassword) {
		UserData user = getUser(userName);
		if (user == null) 
			return false;
		if (user.isValidPassword(oldPassword)) {
			user.setPassword(newPassword);
			putUser(user);
			return true;
		}
		return false;
	}
	
	private boolean putUser(UserData user) {
		String created;
		if (user.getCreated() == null) {
	        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZ");
	        created = sdf.format(new Date());
		} else {
			created = user.getCreated();
		}
		
		try {
			client.prepareIndex(HTTP_USER_AUTH_INDEX, HTTP_USER_AUTH_TYPE, user.getUsername())
			        .setSource(jsonBuilder()
			                    .startObject()
			                        .field("username", user.getUsername())
			                        .field("password", user.getPassword())
                                    .field("indices", user.getIndexFilters())
			                        .field("created", created)
			                    .endObject()
			                  )
			        .setRefreshPolicy(IMMEDIATE)
			        .execute()
			        .get();
			reloadUserDataCache();
			return true;
		} catch (InterruptedException | ExecutionException e) {
			EFLogger.error("InterruptedException | ExecutionException", e);
		} catch (ElasticsearchException e) {
			EFLogger.error("ElasticsearchException", e);
		} catch (IOException e) {
			EFLogger.error("IOException", e);
		}
		return false;
	}
	
	private UserData getUser(String userName) {
		GetResponse response = null;
		try {
			response = client.prepareGet(HTTP_USER_AUTH_INDEX, HTTP_USER_AUTH_TYPE, userName)
			        .setOperationThreaded(false)
			        .execute()
			        .get();
		} catch (InterruptedException | ExecutionException e) {
			EFLogger.error("InterruptedException | ExecutionException", e);
		}
		if (response != null && response.isExists()) {
			return getUserDataFromESSource(response.getSource());
		}
		
		return null;
	}

	public boolean deleteUser(String userName) {
		DeleteResponse response = null;
		try {
			response = client.prepareDelete(HTTP_USER_AUTH_INDEX, HTTP_USER_AUTH_TYPE, userName)
					.setRefreshPolicy(IMMEDIATE)
			        .execute()
			        .get();
		} catch (InterruptedException | ExecutionException e) {
			EFLogger.error("InterruptedException | ExecutionException", e);
		}
		if (response != null && response.getResult().equals(DocWriteResponse.Result.DELETED)) {
			reloadUserDataCache();
			return true;
		}
		
		return false;
	}
	
	private boolean createIndexIfEmpty(int number_of_shards, int number_of_replicas) {
		IndicesExistsResponse res = client.admin().indices().prepareExists(HTTP_USER_AUTH_INDEX).execute().actionGet();
		if (res.isExists()) {
			return true;
		}

        Settings indexSettings = Settings.builder()
                .put("number_of_shards", number_of_shards)
                .put("number_of_replicas", number_of_replicas).build();

		CreateIndexRequest request = new CreateIndexRequest(HTTP_USER_AUTH_INDEX, indexSettings);
		CreateIndexResponse resp = client.admin().indices().create(request).actionGet();
		if (resp.isAcknowledged()) {
			try {
				// wait the creation process has been completed 
				Thread.sleep(2000);
			} catch (InterruptedException e) {
			}
			return true;
		}
		return false;
	}

	private void reloadUserDataCache() {
		List<UserData> userDataList = getAllUserData();
		if (userDataList == null) {
			return ;
		}
		UserAuthenticator.reloadUserDataCache(userDataList);
		isInitialized = true;
	}
	
	/**
	 * get all user info
	 */
	private List<UserData> getAllUserData() {
		try {
			SearchResponse res = client.prepareSearch()
					.setIndices(HTTP_USER_AUTH_INDEX)
					.setTypes(HTTP_USER_AUTH_TYPE)
			        .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			        .setQuery(QueryBuilders.matchAllQuery())
					.setSize(100)
					.execute()
					.get();
			if (res.getFailedShards() == 0 && res.getHits() != null && res.getHits().hits() != null) {
				List<UserData> userDataList = Lists.newCopyOnWriteArrayList();
				SearchHit[] hits = res.getHits().hits();
				for (int i = 0; i < hits.length; i++) {
					SearchHit hit = hits[i];
					userDataList.add(getUserDataFromESSource(hit.getSource()));
				}
				return userDataList;
			} else {
				EFLogger.error("Failed to get data from some shards");
				return null;
			}
		} catch (InterruptedException | ExecutionException e) {
			EFLogger.error("InterruptedException | ExecutionException", e);
		} catch (Exception ex) {
			// possibly the ES's loading process hasn't finished yet 
			EFLogger.error("failed to load all user info", ex);
		}
		return null;
	}
	
	private UserData getUserDataFromESSource(Map<String, Object> source) {
		String userName = (String)source.get("username");
		String password = (String)source.get("password");
		String created  = (String)source.get("created");

        HashMap<String, HashMap<String, Boolean>> indices;
        if (source.containsKey("indices")) {
            @SuppressWarnings("unchecked")
            HashMap<String, HashMap<String, Boolean>> indicesList = (HashMap<String, HashMap<String, Boolean>>)source.get("indices");
            indices = indicesList;
        } else {
            indices = new HashMap<String, HashMap<String, Boolean>>();
        }

		return UserData.restoreFromESData(userName, password, created, indices);
	}
}
