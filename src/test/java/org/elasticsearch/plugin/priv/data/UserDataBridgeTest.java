package org.elasticsearch.plugin.priv.data;

import org.elasticsearch.plugin.elasticfence.data.UserData;
import org.junit.Test;

import java.util.HashMap;


public class UserDataBridgeTest {
	@Test
	public void listUserTest() {
        HashMap<String, HashMap<String, Boolean>> filters = new HashMap<>();
        filters.put("test_indexw", new HashMap<String, Boolean>()
                {{
                    put("read", true);
                    put("write", true);
                    put("add", true);
                    put("delete", true);
                }}
        );

		UserData userData = new UserData("tess_user", "test_password", filters);
		System.out.println(userData.toJSON());
	}
}
