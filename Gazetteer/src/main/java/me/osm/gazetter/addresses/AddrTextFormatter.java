package me.osm.gazetter.addresses;

import java.util.List;

import org.json.JSONObject;

public interface AddrTextFormatter {

	String joinNames(List<JSONObject> addrJsonRow, JSONObject properties, String lang);

	String joinBoundariesNames(List<JSONObject> result, String lang);

}