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
import epmc.expression.Expression;
import epmc.prism.exporter.processor.JANI2PRISMProcessorStrict;
import epmc.prism.exporter.processor.ProcessorRegistrar;

public class DestinationProcessor implements JANI2PRISMProcessorStrict {

	private Destination destination = null;
	private String prefix = null;
	
	@Override
	public void setElement(Object obj) throws EPMCException {
		assert obj != null;
		assert obj instanceof Destination; 
		
		destination = (Destination) obj;
	}

	@Override
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public StringBuilder toPRISM() throws EPMCException {
		assert destination != null;
		
		StringBuilder prism = new StringBuilder();
		JANI2PRISMProcessorStrict processor; 
		
		if (prefix != null)	{
			prism.append(prefix);
		}
		
		Expression prob = destination.getProbabilityExpressionOrOne(); 
		processor = ProcessorRegistrar.getProcessor(prob);
		prism.append(processor.toPRISM().toString());
		
		prism.append(" : ");
		
		Assignments assignments = destination.getAssignments();
		processor = ProcessorRegistrar.getProcessor(assignments);
		prism.append(processor.toPRISM().toString());

		return prism;
	}
}
