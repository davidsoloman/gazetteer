package me.osm.gazetter.addresses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressesParser {
	
	private static final String ADDR_NAMES = "names";

	private static final String ADDR_NAME = "name";

	private static final String ADDR_LVL = "lvl";

	private static final String ADDR_PARTS = "parts";

	private static final String ADDR_TEXT = "text";

	private static final String ADDR_SCHEME = "addr-scheme";

	private static final String ADDR_FULL = "addr:full";

	private static final Logger log = LoggerFactory.getLogger(AddressesParser.class.getName());
	
	private static final Map<String, Integer> type2level = new HashMap<>();
	static {
		type2level.put("hn", 10);
		type2level.put("street", 20);
		type2level.put("place:quarter", 30);
		type2level.put("place:neighbourhood", 40);
		type2level.put("place:suburb", 50);
		type2level.put("place:allotments", 60);
		type2level.put("place:locality", 70);
		type2level.put("place:isolated_dwelling", 70);
		type2level.put("place:village", 70);
		type2level.put("place:hamlet", 70);
		type2level.put("place:town", 70);
		type2level.put("place:city", 70);

		type2level.put("boundary:8", 80);
		type2level.put("boundary:6", 90);
		type2level.put("boundary:5", 100);
		type2level.put("boundary:4", 110);
		type2level.put("boundary:3", 120);
		type2level.put("boundary:2", 130);
	}
	

	public JSONArray parse(JSONObject addrPoint, List<JSONObject> boundaries, List<JSONObject> nearbyStreets) {
		
		JSONArray result = new JSONArray();
		
		JSONObject properties = addrPoint.getJSONObject("properties");
		List<JSONObject> addresses = parseSchemes(properties);
		
		for(JSONObject addrRow : addresses) {
			List<JSONObject> addrJsonRow = new ArrayList<>();
			
			addrJsonRow.add(hnAsJSON(addrPoint, addrRow));
			
			JSONObject streetAsJSON = streetAsJSON(addrPoint, addrRow, null, nearbyStreets);
			if(streetAsJSON != null) {
				addrJsonRow.add(streetAsJSON);
			}
			
			JSONObject quarterJSON = null;
			if(addrRow.has("addr:quarter")) {
				quarterJSON = new JSONObject();
				quarterJSON.put(ADDR_NAME, addrRow.getString("addr:quarter"));
				quarterJSON.put(ADDR_LVL, type2level.get("place:quarter"));
				addrJsonRow.add(quarterJSON);
			}

			JSONObject cityJSON = null;
			if(addrRow.has("addr:city")) {
				cityJSON = new JSONObject();
				cityJSON.put(ADDR_NAME, addrRow.getString("addr:city"));
				cityJSON.put(ADDR_LVL, type2level.get("place:city"));
				addrJsonRow.add(cityJSON);
			}
			
			for(JSONObject bndry : boundaries) {
				int addrLevel = getAddrLevel(bndry); 
				if(addrLevel >= 0) {
					
					if(quarterJSON != null && addrLevel == type2level.get("place:quarter")) {
						joinAddrLvl(quarterJSON, bndry);
						continue;
					}

					if(cityJSON != null && addrLevel == type2level.get("place:city")) {
						joinAddrLvl(cityJSON, bndry);
						continue;
					}
					
					JSONObject addrLVL = new JSONObject();
					addrLVL.put("lnk", bndry.getString("id"));
					
					Map<String, String> nTags = AddressesUtils.filterNameTags(bndry);
					
					if(!nTags.containsKey(ADDR_NAME)) {
						continue;
					}
					
					addrLVL.put(ADDR_LVL, addrLevel);
					addrLVL.put(ADDR_NAME, nTags.get(ADDR_NAME));
					addrLVL.put(ADDR_NAMES, new JSONObject(nTags));
					
					addrJsonRow.add(addrLVL);
				}
			}
			
			Collections.sort(addrJsonRow, new Comparator<JSONObject>() {

				@Override
				public int compare(JSONObject o1, JSONObject o2) {
					int i1 = o1.getInt(ADDR_LVL);
					int i2 = o2.getInt(ADDR_LVL);
					return i1 - i2;
				}
				
			});
			
			JSONObject fullAddressRow = new JSONObject();
			fullAddressRow.put(ADDR_TEXT, joinNames(addrJsonRow));
			fullAddressRow.put(ADDR_PARTS, new JSONArray(addrJsonRow));
			fullAddressRow.put(ADDR_SCHEME, addrRow.optString(ADDR_SCHEME));
			
			result.put(fullAddressRow);
		}
		
		if(StringUtils.isNotBlank(properties.optString(ADDR_FULL))) {
			JSONObject addrFull = new JSONObject();
			addrFull.put(ADDR_TEXT, properties.optString(ADDR_FULL));
			addrFull.put(ADDR_SCHEME, properties.optString(ADDR_FULL));
			result.put(addrFull);
		}
		
		return result;
	}

	private static String joinNames(List<JSONObject> addrJsonRow) {
		
		StringBuilder sb = new StringBuilder();
		
		for(JSONObject lvl : addrJsonRow) {
			sb.append(", ").append(lvl.getString(ADDR_NAME));
		}
		
		if(sb.length() > 2) {
			return sb.substring(2);
		}
		
		return null;
	}

	private void joinAddrLvl(JSONObject baseJSON, JSONObject bndry) {
		baseJSON.put("lnk", bndry.getString("id"));
		baseJSON.put(ADDR_NAMES, AddressesUtils.filterNameTags(bndry));
	}

	private JSONObject streetAsJSON(JSONObject addrPoint, JSONObject addrRow, 
			JSONObject associatedStreet, List<JSONObject> nearbyStreets) {
		
		if(!addrRow.has("addr:street")) {
			return null;
		}
		
		String street = addrRow.getString("addr:street");

		if(associatedStreet == null && nearbyStreets != null) {
			for(JSONObject ls : nearbyStreets) {
				if(containValue(street, AddressesUtils.filterNameTags(ls))) {
					associatedStreet = ls;
					break;
				}
			}
		}
		
		JSONObject streetAddrPart = new JSONObject();
		streetAddrPart.put(ADDR_NAME, street);
		streetAddrPart.put(ADDR_LVL, type2level.get("street"));
		
		if(associatedStreet != null) {
			streetAddrPart.put(ADDR_NAMES, new JSONObject(AddressesUtils.filterNameTags(associatedStreet)));
			streetAddrPart.put("lnk", associatedStreet.optString("id"));
		}
		
		return streetAddrPart;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private boolean containValue(String street, Map<String, String> filterNameTags) {
		return new HashSet(filterNameTags.values()).contains(street); 
	}

	private JSONObject hnAsJSON(JSONObject addrPoint, JSONObject addrRow) {
		JSONObject hnAddrPart = new JSONObject();
		
		String hn = addrRow.optString("addr:housenumber");
		hnAddrPart.put(ADDR_NAME, hn);
		hnAddrPart.put(ADDR_LVL, type2level.get("hn"));
		hnAddrPart.put("lnk", addrPoint.optString("id"));

		JSONObject names = new JSONObject();
		hnAddrPart.put(ADDR_NAMES, names);
		
		if(addrRow.has("addr:housename")) {
			names.put("addr:housename", addrRow.getString("addr:housename"));
		}

		if(addrRow.has("addr:hn-orig")) {
			names.put("addr:hn-orig", addrRow.getString("addr:hn-orig"));
		}
		
		return hnAddrPart;
	}

	private static int getAddrLevel(JSONObject obj) {
		
		JSONObject properties = obj.optJSONObject("properties");
		
		if(properties == null) {
			properties = obj;
		}
		
		String pk = "place:" + properties.optString("place");
		if(type2level.containsKey(pk)) {
			return type2level.get(pk);
		}

		String bk = "boundary:" + properties.optString("admin_level").trim();
		if(type2level.containsKey(bk)) {
			return type2level.get(bk);
		}
		
		return -1;
	}

	private static List<JSONObject> parseSchemes(JSONObject properties) {
		
		List<JSONObject> result = new ArrayList<>();
		
		// addr:...2
		if(properties.has("addr:housenumber2")) {
			
			JSONObject addr1 = copy(properties);
			addr1.put(ADDR_SCHEME, "addr:hn2-1");
			result.add(addr1);

			String hn2 = properties.optString("addr:housenumber2");
			String street2 = properties.optString("addr:street2");
			
			JSONObject addr2 = new JSONObject(properties);
			if(StringUtils.isNotEmpty(hn2)) {
				addr2.put("addr:housenumber", hn2);
				addr2.put(ADDR_SCHEME, "addr:hn2-2");
				if(StringUtils.isNotEmpty(street2)) {
					addr2.put("addr:street", street2);
				}
				else if(addr2.has("addr:street")) {
					addr2.remove("addr:street");
					log.warn("Ambvalent addresses {}", properties);
				}
				
				result.add(addr2);
			}
		}
		//addr:hn=1/2 addr:street1 addr:street2
		else if(properties.has("addr:street2")) {
			String hn = properties.optString("addr:housenumber");

			String[] split = StringUtils.split(hn, "/\\;");
			
			if(split.length == 2) {
				String s1 = properties.optString("addr:street");
				String s2 = properties.optString("addr:street2");
				
				if(StringUtils.isEmpty(s1) || StringUtils.isEmpty(s2)) {
					return Collections.singletonList(properties); 
				}
				
				JSONObject addr1 = copy(properties);
				addr1.put("addr:housenumber", split[0]);
				addr1.put("addr:hn-orig", hn);
				addr1.put(ADDR_SCHEME, "addr:street2-1");
				result.add(addr1);

				JSONObject addr2 = copy(properties);
				addr2.put("addr:housenumber", split[1]);
				addr2.put("addr:street", s2);
				addr2.put("addr:hn-orig", hn);
				addr2.put(ADDR_SCHEME, "addr:street2-2");
				result.add(addr2);
			}
			else {
				result.add(properties);
			}
		}
		//AddrN
		//TODO: search for all addrN levels and Ns
		else if(properties.has("addr2:housenumber")) {
			JSONObject addr1 = copy(properties);
			addr1.put(ADDR_SCHEME, "addrN-1");
			result.add(addr1);

			JSONObject addr2 = copy(properties);
			addr2.put("addr:housenumber", properties.optString("addr2:housenumber"));
			addr2.put("addr:street", properties.optString("addr:street2"));
			addr2.put(ADDR_SCHEME, "addrN-2");
			result.add(addr2);
		}
		else {
			JSONObject addr1 = copy(properties);
			addr1.put(ADDR_SCHEME, "regular");
			result.add(addr1);
		}
		
		return result;
	}
	
	private static JSONObject copy(JSONObject properties) {
		Set<String> keys = properties.keySet();
		return new JSONObject(properties, keys.toArray(new String[keys.size()]));
	}

	public static JSONObject boundariesAsArray(List<JSONObject> input) {
		List<JSONObject> result = new ArrayList<>();
		
		for(JSONObject bndry : input) {
			int addrLevel = getAddrLevel(bndry); 
			if(addrLevel >= 0) {
				
				JSONObject addrLVL = new JSONObject();
				addrLVL.put("lnk", bndry.getString("id"));
				
				Map<String, String> nTags = AddressesUtils.filterNameTags(bndry);
				
				if(!nTags.containsKey(ADDR_NAME)) {
					continue;
				}
				
				addrLVL.put(ADDR_LVL, addrLevel);
				addrLVL.put(ADDR_NAME, nTags.get(ADDR_NAME));
				addrLVL.put(ADDR_NAMES, new JSONObject(nTags));
				
				result.add(addrLVL);
			}
		}

		JSONObject fullAddressRow = new JSONObject();
		Collections.sort(result, new Comparator<JSONObject>() {
			
			@Override
			public int compare(JSONObject o1, JSONObject o2) {
				int i1 = o1.getInt(ADDR_LVL);
				int i2 = o2.getInt(ADDR_LVL);
				return i1 - i2;
			}
			
		});
		
		fullAddressRow.put(ADDR_TEXT, joinNames(result));
		fullAddressRow.put(ADDR_PARTS, new JSONArray(result));
		
		return fullAddressRow;
	} 
	
}