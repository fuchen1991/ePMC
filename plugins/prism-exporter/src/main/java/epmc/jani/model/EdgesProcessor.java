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

package epmc.jani.model;

import epmc.error.EPMCException;
import epmc.prism.exporter.processor.JANI2PRISMProcessorStrict;
import epmc.prism.exporter.processor.ProcessorRegistrar;

public class EdgesProcessor implements JANI2PRISMProcessorStrict {

	private Edges edges = null;
	private String prefix = null;
	
	@Override
	public void setElement(Object obj) throws EPMCException {
		assert obj != null;
		assert obj instanceof Edges; 
		
		edges = (Edges) obj;
	}

	@Override
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}
	
	@Override
	public StringBuilder toPRISM() throws EPMCException {
		assert edges != null;
		
		StringBuilder prism = new StringBuilder();
		JANI2PRISMProcessorStrict processor; 
		
		for (Edge edge : edges) {
			processor = ProcessorRegistrar.getProcessor(edge);
			processor.setPrefix(prefix);
			prism.append(processor.toPRISM().toString());
		}
		
		return prism;
	}
	
	@Override
	public void validateTransientVariables() throws EPMCException {
		assert edges != null;
		
		for (Edge edge : edges) {
			ProcessorRegistrar.getProcessor(edge).validateTransientVariables();
		}
	}

	@Override
	public boolean usesTransientVariables() throws EPMCException {
		assert edges != null;
		
		boolean usesTransient = false;
		for (Edge edge : edges) {
			usesTransient |= ProcessorRegistrar.getProcessor(edge).usesTransientVariables();
		}
		
		return usesTransient;
	}	
}