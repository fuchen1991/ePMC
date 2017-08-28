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

import epmc.value.Type;

/**
 * Marker interface for exact types.
 * Values generated by types implementing this interface are exact. Thus,
 * all basic calculations provided by an {@link OperatorEvaluator} lead to
 * exact results. This excludes e.g. floating point types.
 * 
 * @author Ernst Moritz Hahn
 */
public interface TypeExact extends Type {
	/**
	 * Checks whether type is exact.
	 * 
	 * @param type type to be checked
	 * @return {@code true} iff type is exact
	 */
	static boolean isExact(Type type) {
		return type instanceof TypeExact;
	}
}
