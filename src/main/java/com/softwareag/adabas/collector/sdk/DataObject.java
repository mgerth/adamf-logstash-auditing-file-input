/*
 * Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates and/or their licensors.
 */

package com.softwareag.adabas.collector.sdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * @author Matthias Gerth
 */

public class DataObject {
	private String name;
	private HashMap<String, Object> list = new HashMap<>();
	private Date date;

	private static final String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

	public DataObject(String name) {
		this(name, new Date());
	}

	public DataObject(String name, Date date) {
		this.name = name;
		this.date = date;
	}

	public void put(String key, Object value) {
		list.put(key, value);
	}

	public void putAll(HashMap<String, Object> map) {
		list.putAll(map);
	}

	public String getName() {
		return name;
	}

	public HashMap<String, Object> getList() {
		return list;
	}

	/**
	 * @param list the list to set
	 */
	public void setList(HashMap<String, Object> list) {
		this.list = list;
	}

	/**
	 * @return the date
	 */
	public Date getDate() {
		return date;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(dumpDataObject(this, 1));
		return (sb.toString());
	}

	private StringBuffer dumpDataObject(DataObject dataObject, int level) {
		StringBuffer indent = new StringBuffer();
		for (int i = 0; i < level; i++) {
			indent.append(' ');
		}
		indent.append(level).append(' ');
		StringBuffer sb = new StringBuffer();
		for (String key : dataObject.list.keySet()) {
			sb.append(indent).append(key);
			Object object = dataObject.list.get(key);
			if (object instanceof DataObject) {
				sb.append(System.lineSeparator());
				sb.append(dumpDataObject((DataObject) object, level + 1));
			} else {
				if (object instanceof List<?>) {
					int index = 0;
					for (Object obj : (List<?>) object) {
						if (obj instanceof DataObject) {
							if (index == 0) {
								sb.append("[" + index++ + "]: ").append(System.lineSeparator());
							} else {
								sb.append(indent).append(key + "[" + index++ + "]: ")
										.append(System.lineSeparator());
							}
							if (obj != null) {
								sb.append(dumpDataObject((DataObject) obj, level + 1));
							}
						} else {
							if (index == 0) {
								sb.append(System.lineSeparator());
							}
							if (obj != null) {
								sb.append(indent).append(
										"  [" + index++ + "] {" + obj.getClass() + "}: " + obj)
										.append(System.lineSeparator());
							} else {
								sb.append(indent)
										.append(" " + key + "[" + index++ + "] {null}: null")
										.append(System.lineSeparator());
							}
						}
					}
					if (((List<?>) object).size() == 0) {
						sb.append(System.lineSeparator());
					}
				} else {
					if (object != null) {
						sb.append(" {" + object.getClass() + "}: " + object)
								.append(System.lineSeparator());
					} else {
						sb.append(" {null}: null").append(System.lineSeparator());
					}
				}
			}
		}
		return sb;
	}

	public JsonObject toJSON() {
		Gson gson = new GsonBuilder().setDateFormat(dateFormat).create();
		JsonObject jDataObject = new JsonObject();

		for (String key : list.keySet()) {
			if (list.get(key) instanceof ArrayList) {
				JsonArray jsonArray = new JsonArray();
				for (Object element : (ArrayList<?>) list.get(key)) {
					if (element instanceof DataObject)
						jsonArray.add(((DataObject) element).toJSON());
					else
						jsonArray.add(gson.toJson(element));
				}
				jDataObject.add(key, jsonArray);
			} else if (list.get(key) instanceof DataObject)
				jDataObject.add(key, ((DataObject) list.get(key)).toJSON());
			else if (list.get(key) instanceof LinkedHashMap<?, ?>) {
				jDataObject.add(key, gson.toJsonTree(list.get(key)));
			} else
				jDataObject.add(key, gson.toJsonTree(list.get(key)));
		}

		return jDataObject;
	}

	public String toJSONString() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson(toJSON());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((list == null) ? 0 : list.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DataObject other = (DataObject) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (list == null) {
			if (other.list != null)
				return false;
		} else if (!list.equals(other.list))
			return false;
		return true;
	}

}
