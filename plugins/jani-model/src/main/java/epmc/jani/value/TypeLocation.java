package epmc.jani.value;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import epmc.jani.model.Location;
import epmc.jani.model.Locations;
import epmc.value.ContextValue;
import epmc.value.Type;
import epmc.value.TypeArray;
import epmc.value.TypeArrayGeneric;
import epmc.value.TypeEnumerable;
import epmc.value.TypeNumBitsKnown;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

/**
 * Type to generate values storing a location from a set of locations.
 * Locations are mapped to number from zero to the number of locations minus
 * one.
 * 
 * @author Ernst Moritz Hahn
 */
public final class TypeLocation implements TypeEnumerable, TypeNumBitsKnown {
	/** 1L, as I don't know any better. */
	private static final long serialVersionUID = 1L;
	/** String used for the {@link #toString()} method. */
	private final static String LOCATION = "location";

	/** Number of bits used to store a value of this type. */
	private int numBits;
	/** Context to which this type belongs. */
	private ContextValue context;
	/** Set of locations which this type represents. */
//	private Locations locations;
	/** Map to enumerate locations. */
	private TObjectIntMap<String> locationToNumber = new TObjectIntHashMap<>();
	/** Maps a number to corresponding location. */
	private String[] numberToLocation;
	private List<String> locations;

	TypeLocation(ContextValue context, Locations locations) {
		this(context, locationsToStringList(locations));
		assert context != null;
		assert locations != null;
		this.context = context;
		this.numberToLocation = new String[locations.size()];
		int locNr = 0;
		for (Location location : locations) {
			locationToNumber.put(location.getName(), locNr);
			numberToLocation[locNr] = location.getName();
			locNr++;
		}
		numBits = Integer.SIZE - Integer.numberOfLeadingZeros(locations.size() - 1);
	}

	/**
	 * Generate a new location storing type.
	 * None of the parameters may be {@code null}.
	 * 
	 * @param context context to use for this type
	 * @param locations set of locations
	 */
	TypeLocation(ContextValue context, List<String> locations) {
		assert context != null;
		assert locations != null;
		this.context = context;
		this.numberToLocation = new String[locations.size()];
		int locNr = 0;
		for (String location : locations) {
			locationToNumber.put(location, locNr);
			numberToLocation[locNr] = location;
			locNr++;
		}
		this.locations = locations;
		numBits = Integer.SIZE - Integer.numberOfLeadingZeros(locations.size() - 1);
	}

	private static List<String> locationsToStringList(Locations locations) {
		assert locations != null;
		List<String> result = new ArrayList<>();
		for (Location location : locations) {
			result.add(location.getName());
		}
		return result;
	}
	
	@Override
	public ContextValue getContext() {
		return context;
	}

	@Override
	public ValueLocation newValue() {
		return new ValueLocation(this);
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
        hash = locations.hashCode() + (hash << 6) + (hash << 16) - hash;
		return hash;
	}
	
	@Override
	public boolean equals(Object obj) {
		assert obj != null;
		if (!(obj instanceof TypeLocation)) {
			return false;
		}
		TypeLocation other = (TypeLocation) obj;
		if (this.context != other.context) {
			return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append(LOCATION);
		Set<String> locationToNumber = new LinkedHashSet<>();
		for (String location : numberToLocation) {
			locationToNumber.add(location);
		}
		result.append(locationToNumber);
		return result.toString();
	}
	
	@Override
	public int getNumBits() {
		return numBits;
	}
	
	/**
	 * Get location by its number.
	 * The parameter must be a valid location number.
	 * 
	 * @param locationNumber number of the location
	 * @return location with this number
	 */
	public String getLocation(int locationNumber) {
		assert locationNumber >= 0;
		assert locationNumber < numberToLocation.length;
		return numberToLocation[locationNumber];
	}
	
	/**
	 * Get the number of a location.
	 * The parameter may not be {@code null}.
	 * 
	 * @param location location of which to get the number
	 * @return number of the location
	 */
	public int getNumber(Location location) {
		assert location != null;
		assert locationToNumber.containsKey(location.getName());
		return locationToNumber.get(location);
	}

	/**
	 * Check whether a location is contained in the locations of this type.
	 * The location parameter may not be {@code null}.
	 * 
	 * @param location location of which to check whether it is contained
	 * @return {@code true} if contained, else {@code false}
	 */
	public boolean contains(Location location) {
		assert location != null;
		return locationToNumber.containsKey(location);
	}
	
	/**
	 * Generate a new value representing the given location.
	 * The given location may not be {@code null}.
	 * 
	 * @param location location to generate value of
	 * @return value representing the given location
	 */
	public ValueLocation newValue(Location location) {
		assert location != null;
		assert locationToNumber.containsKey(location);
		ValueLocation result = newValue();
		result.set(location);
		return result;
	}

	/**
	 * Obtain the number of possible locations of this type.
	 * 
	 * @return number of locations of this type
	 */
	int getNumLocations() {
		return locationToNumber.size();
	}
	
	@Override
	public int getNumValues() {
		return locations.size();
	}

	@Override
    public TypeArray getTypeArray() {
        return getContext().makeUnique(new TypeArrayGeneric(this));
    }
	
	@Override
	public boolean canImport(Type type) {
        assert type != null;
        if (this == type) {
            return true;
        }
        return false;
	}
}