/*******************************************************************************
 * Copyright 2015 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package de.tomgrill.gdxfacebook.core;

import com.badlogic.gdx.utils.ArrayMap;

/**
 * Build a proper Facebook Graph API request with this class.
 * 
 * @see <a
 *      href="https://developers.facebook.com/docs/graph-api/using-graph-api/">https://developers.facebook.com/docs/graph-api/using-graph-api/</a>
 *      for more information on how Facebook Graph API works.
 * 
 * 
 * @author Thomas Pronold (TomGrill) mail@tomgrill.de
 *
 */
public class GDXFacebookGraphRequest {

	private String url = "https://graph.facebook.com/";

	private String node = null;

	private ArrayMap<String, String> fields;

	private boolean useCurrentAccessToken = false;

	public GDXFacebookGraphRequest() {
		fields = new ArrayMap<String, String>();
	}

	/**
	 * Sets the URL. Only required if you do not want to use the default URL
	 * (https://graph.facebook.com/). F.e. using an older Graph API.
	 * 
	 * @param url
	 * @return
	 */
	public GDXFacebookGraphRequest setUrl(String url) {
		this.url = url.trim();
		if (!this.url.endsWith("/")) {
			this.url += "/";
		}
		return this;
	}

	/**
	 * Sets the node. F.e. "me", "bgolub" @see <a
	 * href="https://developers.facebook.com/docs/graph-api/reference"
	 * >https://developers.facebook.com/docs/graph-api/reference</a>
	 * 
	 * @param node
	 */
	public GDXFacebookGraphRequest setNode(String node) {

		this.node = node.trim();
		if (this.node.startsWith("/")) {
			this.node.replaceFirst("/", "");
		}

		return this;
	}

	/**
	 * Add a field to the request.
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public GDXFacebookGraphRequest putField(String key, String value) {
		fields.put(key, value);
		return this;
	}

	/**
	 * Add multiple fields to the request.
	 * 
	 * @param fields
	 * @return
	 */
	public GDXFacebookGraphRequest putFields(ArrayMap<String, String> fields) {
		fields.putAll(fields);
		return this;
	}

	/**
	 * Call this method when your request requires an access_token field. The
	 * field will be set with the current available access token.
	 * 
	 * @return
	 */
	public GDXFacebookGraphRequest useCurrentAccessToken() {
		this.useCurrentAccessToken = true;
		return this;
	}

	protected boolean isUseCurrentAccessToken() {
		return useCurrentAccessToken;
	}

	protected String build() {
		String request = url;

		if (node != null) {
			request += node;
		}

		request += "?";

		for (int i = 0; i < fields.size; i++) {
			request += fields.getKeyAt(i) + "=" + fields.getValueAt(i);
			if (i + 1 < fields.size) {
				request += "&";
			}
		}

		return request;
	}
}
