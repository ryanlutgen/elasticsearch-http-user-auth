package org.elasticsearch.plugin.elasticfence;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Maps;
import org.elasticsearch.plugin.elasticfence.data.UserData;
import org.elasticsearch.plugin.elasticfence.logger.EFLogger;
import org.elasticsearch.plugin.elasticfence.parser.RequestParser;
import org.elasticsearch.rest.RestRequest;

/**
 * A class for checking an index path is accessible by a user. 
 * @author tk
 */
public class UserAuthenticator {
	private static String rootPassword = "";

	// a map of all users' username => UserData 
	private static Map<String, UserData> users;
	private UserData user;
	
	static {
		users = Maps.newConcurrentMap();
	}
	public UserAuthenticator(String username, String rawPassword) {
		if (users.containsKey(username) && users.get(username).isValidPassword(rawPassword)) {
			user = users.get(username);
		}
	}
	public boolean isValidUser() {
		return user != null;
	}

    public boolean isAccessibleIndices(RequestParser parser, RestRequest.Method method) {
        if (user == null) {
			return false;
		}

		if ("root".equals(user.getUsername())) {
			return true;
		}

        Set<String> filters = user.getIndexFilters();
        HashMap<String, HashMap<String, Boolean>> filters2 = user.getIndexFilters2();
		String apiName = parser.getApiName();
		List<String> indices = parser.getIndicesInPath();
		if (indices.contains("/*")) {
			// /* is only accessible by root. */
			return true;
		}
		
		// check kibana accessibility
		if (isAccessibleUserFilter2(filters2, parser.getPath(), method) ) {
			return true;
		}
		// check kibana accessibility
		if (isKibanaRequest(parser.getPath()) && isAccessibleUserToKibana2(filters2)) {
			return true;
		}

		switch (apiName) {
			case "_msearch":
				try {
					indices = parser.getIndicesFromMsearchRequestBody();
					return checkIndicesWithFilters2(indices, filters2, method);
				} catch (Exception e) {
					EFLogger.error("block _msearch", e);
				}
				return false;
			case "_mget":
				try {
					indices = parser.getIndicesFromMgetRequestBody();
					return checkIndicesWithFilters2(indices, filters2, method);
				} catch (Exception e) {
					EFLogger.error("block _mget", e);
				}
				return false;
			case "_bulk":
				try {
					indices = parser.getIndicesFromBulkRequestBody();
					return checkIndicesWithFilters2(indices, filters2, method);
				} catch (Exception e) {
					EFLogger.error("block _bulk", e);
				}
				return false;
			default:
				break;
		}
		
		// reject if indices contains the empty index ("/") and apiName is not empty
		if (indices.contains("/") && !"".equals(apiName)) {
			return false;
		}
		
		// simply compare path indices and index filters
//		if (filters.containsAll(indices)) {
//			return true;
//		}
		return checkIndicesWithFilters2(indices, filters2, method);
	}
	
	private boolean checkIndicesWithFilters(List<String> indices, Set<String> filters) {
		for (String index : indices) {
			boolean passed = false;
			for (String filter : filters) {
				if (ifFilterCoversIndex(index, filter)) {
					passed = true;
					break;
				}
			}
			if (!passed) {
				return false;
			}
		}
		
		return true;
	}


    private boolean checkIndicesWithFilters2(List<String> indices, HashMap<String, HashMap<String, Boolean>> filters, RestRequest.Method method) {
        for (String index : indices) {
            boolean passed = false;
            for (String filter : filters.keySet()) {
                if (ifFilterCoversIndex(index, filter)) {
                    HashMap<String, Boolean> indexPerms = filters.get(filter);
                    if (checkRights(method, indexPerms)) {
                        passed = true;
                    }
                    break;
                }
            }
            if (!passed) {
                return false;
            }
        }

        return true;
    }
	
	/**
	 * check request path if it is used by kibana
	 * @param requestPath
	 * @return
	 */
	private boolean isKibanaRequest(String requestPath) {
		String index = normalizeUrlPath(requestPath);
		if ("/".equals(requestPath) || "/_nodes".equals(requestPath) || "/.kibana".equals(index) || "/_cluster/health/.kibana".equals(requestPath) || "_mget".equals(index) ) {
			return true;
		}
		
		return false;
	}
	
	/**
	 * check if an user has auth to kibana
	 * @param filters
	 * @return
	 */
    private boolean isAccessibleUserToKibana(Set<String> filters) {
        if (filters.contains("/.kibana")) {
            return true;
        }

        return false;
    }

    private boolean isAccessibleUserToKibana2(HashMap<String, HashMap<String, Boolean>> filters) {
        if (filters.keySet().contains("/.kibana")) {
            return filters.get("/.kibana").get("read");
        }

        return false;
    }
	
	/**
	 * check if an user has filter matching regex rules
	 * @param filters
	 * @param requestPath
	 * @return
	 */
    private boolean isAccessibleUserFilter(Set<String> filters, String requestPath) {
        String index = normalizeUrlPath(requestPath);
        String[] array = filters.toArray(new String[0]);
        for( String filter : array ) {
            // EFLogger.info("Checking url: " + index + " with regex " + filter);
            if (index.matches(filter)) {

                return true;
            }
        }
        return false;
    }

    private boolean isAccessibleUserFilter2(HashMap<String, HashMap<String, Boolean>> filters, String requestPath, RestRequest.Method method) {
        String index = normalizeUrlPath(requestPath);
        for( String filter : filters.keySet()) {
            // EFLogger.info("Checking url: " + index + " with regex " + filter);
            if (index.matches(filter)) {
                HashMap<String, Boolean> indexPerms = filters.get(filter);
                if (checkRights(method, indexPerms)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkRights(RestRequest.Method method, HashMap<String, Boolean> indexPerms) {
        if (method.equals(RestRequest.Method.GET) && indexPerms.get("read")) {
            return true;
        }
        if (method.equals(RestRequest.Method.POST) && indexPerms.get("add")) {
            return true;
        }
        if (method.equals(RestRequest.Method.PUT) && indexPerms.get("add")) {
            return true;
        }
        if (method.equals(RestRequest.Method.DELETE) && indexPerms.get("delete")) {
            return true;
        }

        return false;
    }


    /**
	 * authenticate a combination of user, password and path
	 * @param path
	 * @return
	 */
	public boolean execAuth(String path) {
		if (user == null) {
			return false;
		}
		
		if ("root".equals(user.getUsername())) {
			return true;
		}
		
		// the all matching path /* is only accessible by root. 
		if ("/*".equals(path)) {
			return false;
		}
		
		String index = normalizeUrlPath(path);
		for (String filter : user.getIndexFilters()) {
			if (ifFilterCoversIndex(index, filter)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * load authentication info when ES instance starts
	 */
	public static void loadRootUserDataCacheOnStart() {
		EFLogger.debug("loadRootUserDataCacheOnStart");
		users.put("root", UserData.restoreFromESData("root", rootPassword, "/*"));
	}
	
	/**
	 * reload authentication info
	 */
	public static void reloadUserDataCache(List<UserData> userDataList) {
		Map<String, UserData> users  = Maps.newConcurrentMap();
		if (userDataList != null) {
			for (UserData userData : userDataList) {
				users.put(userData.getUsername(), userData);
			}
		}
		// At last, add root user information
		users.put("root", UserData.restoreFromESData("root", rootPassword, "/*"));
		UserAuthenticator.users = users;
	}

	public static void setRootPassword(String rootPassword) {
		if (rootPassword == null) 
			rootPassword = "";
		UserAuthenticator.rootPassword = UserData.encPassword(rootPassword);
	}

	public static String getRootPassword() {
		if (rootPassword == null) 
			return "";
		return rootPassword;
	}
	
	/**
	 * Whether the filter covers the index
	 * When both of them include "*" character, this function just compares them 
	 * as simple strings (not as regex strings) 
	 * @param index
	 * @param filter
	 * @return
	 */
	private boolean ifFilterCoversIndex(String index, String filter) {
		if (index.startsWith("/")) 
			index = index.substring(1);
		if (filter.startsWith("/")) 
			filter = filter.substring(1);
		
		if (index.equals(filter)) 
			return true;
		
		// processing a special case in advance
		if ("".equals(index) || "".equals(filter)) {
			if ("".equals(filter) && "".equals(index)) {
				return true;
			}
			return false;
		}
		
		if (!filter.contains("*")) {
			if ("*".equals(index)) {
				return true;
			} else {
				// compare as strings
				return index.equals(filter);
			}
		}
		
		// processing regex conditions
		if (index.contains("*")) {
			// just compare if both filter and index include "*" character, too. 
			return index.equals(filter);
		} else {
			// only filter contains "*" char
			String regexStr = "";
			if (!filter.startsWith("*")) 
				regexStr = "^";
			String[] splitStrArr = filter.split("\\*");
			for (int i = 0; i < splitStrArr.length; i++) {
				if (i < splitStrArr.length - 1) {
					if ("".equals(splitStrArr[i])) {
						regexStr += ".*?";
					} else {
						regexStr += Pattern.quote(splitStrArr[i]) + ".*?";
					}
				} else {
					regexStr += Pattern.quote(splitStrArr[i]);
				}
			}

			if (filter.endsWith("*")) 
				regexStr += ".*?$";
			else 
				regexStr += "$";
			Pattern p = Pattern.compile(regexStr);
			Matcher m = p.matcher(index);
			return m.find();
		}
	}
	
	/**
	 * normalizing HTTP URL paths
	 * Ex1: "/test_index/test_type/../../*" => "/*" 
	 * Ex2: "/test_index/test_type/../../../" => "/" 
	 * 
	 * @param path
	 * @return
	 */
	private static String normalizeUrlPath(String path) {
		if ("".equals(path) || path.charAt(0) != '/') {
			path = "/" + path;
		}
		try {
			URI uri = URI.create(path);
			uri = uri.normalize();
			path = uri.toString();
		} catch (IllegalArgumentException ex) {
			EFLogger.error("Illegal path: " + path);
			return null;
		} catch (Exception ex) {
			EFLogger.error("invalid path: " + path);
			return null;
		}
		
		// this case won't occur, but just in case
		if ("".equals(path)) 
			return "/";
		
		// single slash is special path
		if ("/".equals(path)) 
			return "/";
		
		String[] pathInfo = path.split("/");
		String index = "";
		for (String str : pathInfo) {
			// first none-empty string is index name
			if ("".equals(str)) 
				continue;
			index = str;
			break;
		}
		
		if (index.startsWith("_")) 
			return "/";
		return "/" + index;
	}

}
