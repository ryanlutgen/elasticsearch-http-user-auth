package org.elasticsearch.plugin.elasticfence.data;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.codec.digest.DigestUtils;
import com.google.common.collect.Sets;

public class UserData {
	private String username;
	private String encPassword;
    private Set<String> indexFilters;
    private HashMap<String, HashMap<String, Boolean>> indexFilters2;
	private String created;
	private UserData() {
		
	}
	public UserData(String userName, String rawPassword) {
		setUsername(userName);
		setPassword(rawPassword);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZ");
		setCreated(sdf.format(new Date()));

		Set<String> indices = Sets.newConcurrentHashSet();
		setFilters(indices);

        HashMap<String, HashMap<String, Boolean>> indices2 = new HashMap<String, HashMap<String, Boolean>>();
        setFilters2(indices2);

	}

	public UserData(String userName, String rawPassword, Set<String> filters) {
		setUsername(userName);
		setPassword(rawPassword);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZ");
		setCreated(sdf.format(new Date()));
		if (filters == null) {
			filters = Sets.newConcurrentHashSet();
		}
		setFilters(filters);
	}

    public UserData(String userName, String rawPassword, HashMap<String, HashMap<String, Boolean>> filters) {
        setUsername(userName);
        setPassword(rawPassword);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZ");
        setCreated(sdf.format(new Date()));
        if (filters == null) {
            filters = new HashMap<String, HashMap<String, Boolean>>();
        }
        setFilters2(filters);
    }

    //todo
	public UserData(String userName, String rawPassword, String... filters) {
		setUsername(userName);
		setPassword(rawPassword);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZ");
		setCreated(sdf.format(new Date()));
		if (filters == null) {
			Set<String> filterSet = Sets.newConcurrentHashSet(Arrays.asList(filters));
			setFilters(filterSet);
		} else {
			Set<String> filterSet = Sets.newConcurrentHashSet();
			setFilters(filterSet);
		}
	}

	public static UserData restoreFromESData(String username, String encPassword, String created, Set<String> indexFilters, HashMap<String, HashMap<String, Boolean>> indexFilters2) {
		UserData user = new UserData();
		user.username = username;
		user.encPassword = encPassword;
		user.created = created;
		user.indexFilters = indexFilters;
        user.indexFilters2 = indexFilters2;
		return user;
	}

    //todo
	public static UserData restoreFromESData(String username, String encPassword, String... indexFilters) {
		UserData user = new UserData();
		user.username = username;
		user.encPassword = encPassword;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZ");
		user.created = sdf.format(new Date());
		user.indexFilters  = Sets.newConcurrentHashSet(Arrays.asList(indexFilters));
		return user;
	}
	
	public static String encPassword(String rawPassword) {
		return DigestUtils.sha256Hex(rawPassword);
	}
	
	public Set<String> getIndexFilters() {
		return indexFilters;
	}

    public HashMap<String, HashMap<String, Boolean>> getIndexFilters2() {
        return indexFilters2;
    }

	public void setFilters(Set<String> indexFilters) {
		this.indexFilters = indexFilters;
	}

    public void setFilters2(HashMap<String, HashMap<String, Boolean>> indexFilters) {
        this.indexFilters2 = indexFilters;
    }

	public String getPassword() {
		return encPassword;
	}

	public void setPassword(String password) {
		this.encPassword = encPassword(password);
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getCreated() {
		return created;
	}
	
	public void setCreated(String created) {
		this.created = created;
	}
	
	public boolean isValidPassword(String rawPassword) {
		return encPassword.equals(encPassword(rawPassword));
	}
	
	public String toJSON() {
		if (created == null) {
	        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZ");
	        created = sdf.format(new Date());
		}
		try {
			return jsonBuilder()
			.startObject()
			    .field("username", username)
			    .field("password", encPassword)
			    .field("indices", indexFilters)
                .field("indices2", indexFilters2)
			    .field("created", created)
			.endObject().string();
		} catch (IOException e) {
		}
		return "";
	}
}
