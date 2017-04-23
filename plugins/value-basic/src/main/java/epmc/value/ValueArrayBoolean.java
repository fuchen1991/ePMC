/****************************************************************************

    ePMC - an extensible probabilistic model checker
    Copyright (C) 2017

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

*****************************************************************************/

package epmc.value;

import java.util.Arrays;

import epmc.error.EPMCException;
import epmc.value.Value;
import epmc.value.ValueArray;

final class ValueArrayBoolean implements ValueArray {
    private static final int LOG2LONGSIZE = 6;
	private final TypeArrayBoolean type;
    private long[] content;
	private boolean immutable;
	private int size;
    
    ValueArrayBoolean(TypeArrayBoolean type) {
    	this.type = type;
        int numLongs = ((size() - 1) >> LOG2LONGSIZE) + 1;
        this.content = new long[numLongs];
    }
    
	@Override
	public void setSize(int size) {
        assert !isImmutable();
        assert size >= 0;
        int num = ((size - 1) >> LOG2LONGSIZE) + 1;
        this.content = new long[num];
        this.size = size;
	}

	@Override
	public int size() {
		return size;
	}

     @Override
    public void set(Value value, int index) {
        assert !isImmutable();
        assert value != null;
        assert ValueBoolean.isBoolean(value);
        assert index >= 0;
        assert index < size();
        int offset = index >> LOG2LONGSIZE;
        if (ValueBoolean.asBoolean(value).getBoolean()) {
            content[offset] |= 1L << index;
        } else {
            content[offset] &= ~(1L << index);
        }
    }
    
    @Override
    public void get(Value value, int index) {
        assert value != null;
        assert ValueBoolean.isBoolean(value);
        assert index >= 0;
        assert index < size() : index + " " + size();
        int offset = index >> 6;
        ValueBoolean.asBoolean(value).set((content[offset] & (1L << index)) != 0);
    }    
    
    public int nextSetBit(int index) {
        assert index >= 0;
        assert index < size();
        int offset = index >> LOG2LONGSIZE;
        long mask = 1L << index;
        while (offset < content.length) {
            long currentWord = content[offset];
            do {
                if ((currentWord & mask) != 0) {
                    return index;
                }
                mask <<= 1;
                index++;
            } while (mask != 0);
            mask = 1L;
            offset++;
        }
        return -1;
    }
    
    @Override
    public int hashCode() {
    	return Arrays.hashCode(content);
    }
    
    @Override
    public TypeArrayBoolean getType() {
    	return type;
    }
    
    @Override
    public void setImmutable() {
    	immutable = true;
    }
    
    @Override
    public boolean isImmutable() {
    	return immutable;
    }

	@Override
	public void set(String value) throws EPMCException {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public String toString() {
		return UtilValue.arrayToString(this);
	}
}
