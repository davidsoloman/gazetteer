package me.osm.gazetteer.web.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StreetRef implements Ref {

	private String id;
	private String name;
	private List<String> altNames;

	@Override
	public String getId() {
		return id;
	}
	
	@Override
	public void setId(String id) {
		this.id = id;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public void setName(String name) {
		this.name = name;
	}
	
	@Override
	public List<String> getAltNames() {
		return altNames;
	}
	
	@Override
	public void setAltNames(List<String> altNames) {
		this.altNames = altNames;
	}
	
}
