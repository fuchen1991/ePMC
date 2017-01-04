/**CFile***********************************************************************

  FileName    [cuddAddAbs.c]

  PackageName [cudd]

  Synopsis    [Quantification functions for ADDs.]

  Description [External procedures included in this module:
		<ul>
		<li> Cudd_addExistAbstract()
		<li> Cudd_addUnivAbstract()
		<li> Cudd_addOrAbstract()
		<li> Cudd_addMinAbstract()
		<li> Cudd_addMaxAbstract()
		</ul>
	Internal procedures included in this module:
		<ul>
		<li> cuddAddExistAbstractRecur()
		<li> cuddAddUnivAbstractRecur()
		<li> cuddAddOrAbstractRecur()
		<li> cuddAddMinAbstractRecur()
		<li> cuddAddMaxAbstractRecur()
		</ul>
	Static procedures included in this module:
		<ul>
		<li> addCheckPositiveCube()
		</ul>]

  Author      [Fabio Somenzi]

  Copyright   [This file was created at the University of Colorado at
  Boulder.  The University of Colorado at Boulder makes no warranty
  about the suitability of this software for any purpose.  It is
  presented on an AS IS basis.]

******************************************************************************/

#include	"util.h"
#include	"cuddInt.h"

/*---------------------------------------------------------------------------*/
/* Constant declarations                                                     */
/*---------------------------------------------------------------------------*/


/*---------------------------------------------------------------------------*/
/* Stucture declarations                                                     */
/*---------------------------------------------------------------------------*/


/*---------------------------------------------------------------------------*/
/* Type declarations                                                         */
/*---------------------------------------------------------------------------*/


/*---------------------------------------------------------------------------*/
/* Variable declarations                                                     */
/*---------------------------------------------------------------------------*/

#ifndef lint
static char rcsid[] DD_UNUSED = "$Id: cuddAddAbs.c,v 1.14 2004/01/01 06:56:41 fabio Exp $";
#endif

static	DdNode	*two;

/*---------------------------------------------------------------------------*/
/* Macro declarations                                                        */
/*---------------------------------------------------------------------------*/


/**AutomaticStart*************************************************************/

/*---------------------------------------------------------------------------*/
/* Static function prototypes                                                */
/*---------------------------------------------------------------------------*/

static int addCheckPositiveCube (DdManager *manager, DdNode *cube);

/**AutomaticEnd***************************************************************/


/*---------------------------------------------------------------------------*/
/* Definition of exported functions                                          */
/*---------------------------------------------------------------------------*/

/**Function********************************************************************

  Synopsis    [Existentially Abstracts all the variables in cube from f.]

  Description [Abstracts all the variables in cube from f by summing
  over all possible values taken by the variables. Returns the
  abstracted ADD.]

  SideEffects [None]

  SeeAlso     [Cudd_addUnivAbstract Cudd_bddExistAbstract
  Cudd_addOrAbstract]

******************************************************************************/
DdNode *
Cudd_addExistAbstract(
  DdManager * manager,
  DdNode * f,
  DdNode * cube)
{
    DdNode *res;

    two = cuddUniqueConst(manager,(CUDD_VALUE_TYPE) 2);
    if (two == NULL) return(NULL);
    cuddRef(two);

    if (addCheckPositiveCube(manager, cube) == 0) {
        (void) fprintf(manager->err,"Error: Can only abstract cubes");
        return(NULL);
    }

    do {
	manager->reordered = 0;
	res = cuddAddExistAbstractRecur(manager, f, cube);
    } while (manager->reordered == 1);

    if (res == NULL) {
	Cudd_RecursiveDeref(manager,two);
	return(NULL);
    }
    cuddRef(res);
    Cudd_RecursiveDeref(manager,two);
    cuddDeref(res);

    return(res);

} /* end of Cudd_addExistAbstract */


/**Function********************************************************************

  Synopsis    [Universally Abstracts all the variables in cube from f.]

  Description [Abstracts all the variables in cube from f by taking
  the product over all possible values taken by the variable. Returns
  the abstracted ADD if successful; NULL otherwise.]

  SideEffects [None]

  SeeAlso     [Cudd_addExistAbstract Cudd_bddUnivAbstract
  Cudd_addOrAbstract]

******************************************************************************/
DdNode *
Cudd_addUnivAbstract(
  DdManager * manager,
  DdNode * f,
  DdNode * cube)
{
    DdNode		*res;

    if (addCheckPositiveCube(manager, cube) == 0) {
	(void) fprintf(manager->err,"Error:  Can only abstract cubes");
	return(NULL);
    }

    do {
	manager->reordered = 0;
	res = cuddAddUnivAbstractRecur(manager, f, cube);
    } while (manager->reordered == 1);

    return(res);

} /* end of Cudd_addUnivAbstract */


/**Function********************************************************************

  Synopsis    [Disjunctively abstracts all the variables in cube from the
  0-1 ADD f.]

  Description [Abstracts all the variables in cube from the 0-1 ADD f
  by taking the disjunction over all possible values taken by the
  variables.  Returns the abstracted ADD if successful; NULL
  otherwise.]

  SideEffects [None]

  SeeAlso     [Cudd_addUnivAbstract Cudd_addExistAbstract]

******************************************************************************/
DdNode *
Cudd_addOrAbstract(
  DdManager * manager,
  DdNode * f,
  DdNode * cube)
{
    DdNode *res;

    if (addCheckPositiveCube(manager, cube) == 0) {
        (void) fprintf(manager->err,"Error: Can only abstract cubes");
        return(NULL);
    }

    do {
	manager->reordered = 0;
	res = cuddAddOrAbstractRecur(manager, f, cube);
    } while (manager->reordered == 1);
    return(res);

} /* end of Cudd_addOrAbstract */


/**Function********************************************************************

  Synopsis    [Abstracts all the variables in cube from the
  0-1 ADD f by taking the minimum.]

  Description [Abstracts all the variables in cube from the 0-1 ADD f
  by taking the minimum over all possible values taken by the
  variables.  Returns the abstracted ADD if successful; NULL
  otherwise.]

  SideEffects [None]

  SeeAlso     [Cudd_addUnivAbstract Cudd_addExistAbstract]
  
  Note: Added by Dave Parker 14/6/99
  
******************************************************************************/
DdNode *
Cudd_addMinAbstract(
  DdManager * manager,
  DdNode * f,
  DdNode * cube)
{
    DdNode *res;

    if (addCheckPositiveCube(manager, cube) == 0) {
        (void) fprintf(manager->err,"Error: Can only abstract cubes");
        return(NULL);
    }

    do {
	manager->reordered = 0;
	res = cuddAddMinAbstractRecur(manager, f, cube);
    } while (manager->reordered == 1);
    return(res);

} /* end of Cudd_addMinAbstract */


/**Function********************************************************************

  Synopsis    [Abstracts all the variables in cube from the
  0-1 ADD f by taking the maximum.]

  Description [Abstracts all the variables in cube from the 0-1 ADD f
  by taking the maximum over all possible values taken by the
  variables.  Returns the abstracted ADD if successful; NULL
  otherwise.]

  SideEffects [None]

  SeeAlso     [Cudd_addUnivAbstract Cudd_addExistAbstract]
  
  Note: Added by Dave Parker 14/6/99
  
******************************************************************************/
DdNode *
Cudd_addMaxAbstract(
  DdManager * manager,
  DdNode * f,
  DdNode * cube)
{
    DdNode *res;

    if (addCheckPositiveCube(manager, cube) == 0) {
        (void) fprintf(manager->err,"Error: Can only abstract cubes");
        return(NULL);
    }

    do {
	manager->reordered = 0;
	res = cuddAddMaxAbstractRecur(manager, f, cube);
    } while (manager->reordered == 1);
    return(res);

} /* end of Cudd_addMaxAbstract */


/*---------------------------------------------------------------------------*/
/* Definition of internal functions                                          */
/*---------------------------------------------------------------------------*/


/**Function********************************************************************

  Synopsis    [Performs the recursive step of Cudd_addExistAbstract.]

  Description [Performs the recursive step of Cudd_addExistAbstract.
  Returns the ADD obtained by abstracting the variables of cube from f,
  if successful; NULL otherwise.]

  SideEffects [None]

  SeeAlso     []

******************************************************************************/
DdNode *
cuddAddExistAbstractRecur(
  DdManager * manager,
  DdNode * f,
  DdNode * cube)
{
    DdNode	*T, *E, *res, *res1, *res2, *zero;

    statLine(manager);
    zero = DD_ZERO(manager);

    /* Cube is guaranteed to be a cube at this point. */	
    if (f == zero || cuddIsConstant(cube)) {  
        return(f);
    }

    /* Abstract a variable that does not appear in f => multiply by 2. */
    if (cuddI(manager,f->index) > cuddI(manager,cube->index)) {
	res1 = cuddAddExistAbstractRecur(manager, f, cuddT(cube));
	if (res1 == NULL) return(NULL);
	cuddRef(res1);
	/* Use the "internal" procedure to be alerted in case of
	** dynamic reordering. If dynamic reordering occurs, we
	** have to abort the entire abstraction.
	*/
	res = cuddAddApplyRecur(manager,Cudd_addTimes,res1,two);
	if (res == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    return(NULL);
	}
	cuddRef(res);
	Cudd_RecursiveDeref(manager,res1);
	cuddDeref(res);
        return(res);
    }

    if ((res = cuddCacheLookup2(manager, Cudd_addExistAbstract, f, cube)) != NULL) {
	return(res);
    }

    T = cuddT(f);
    E = cuddE(f);

    /* If the two indices are the same, so are their levels. */
    if (f->index == cube->index) {
	res1 = cuddAddExistAbstractRecur(manager, T, cuddT(cube));
	if (res1 == NULL) return(NULL);
        cuddRef(res1);
	res2 = cuddAddExistAbstractRecur(manager, E, cuddT(cube));
	if (res2 == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    return(NULL);
	}
        cuddRef(res2);
	res = cuddAddApplyRecur(manager, Cudd_addPlus, res1, res2);
	if (res == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    Cudd_RecursiveDeref(manager,res2);
	    return(NULL);
	}
	cuddRef(res);
	Cudd_RecursiveDeref(manager,res1);
	Cudd_RecursiveDeref(manager,res2);
	cuddCacheInsert2(manager, Cudd_addExistAbstract, f, cube, res);
	cuddDeref(res);
        return(res);
    } else { /* if (cuddI(manager,f->index) < cuddI(manager,cube->index)) */
	res1 = cuddAddExistAbstractRecur(manager, T, cube);
	if (res1 == NULL) return(NULL);
        cuddRef(res1);
	res2 = cuddAddExistAbstractRecur(manager, E, cube);
	if (res2 == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    return(NULL);
	}
        cuddRef(res2);
	res = (res1 == res2) ? res1 :
	    cuddUniqueInter(manager, (int) f->index, res1, res2);
	if (res == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    Cudd_RecursiveDeref(manager,res2);
	    return(NULL);
	}
	cuddDeref(res1);
	cuddDeref(res2);
	cuddCacheInsert2(manager, Cudd_addExistAbstract, f, cube, res);
        return(res);
    }	    

} /* end of cuddAddExistAbstractRecur */


/**Function********************************************************************

  Synopsis    [Performs the recursive step of Cudd_addUnivAbstract.]

  Description [Performs the recursive step of Cudd_addUnivAbstract.
  Returns the ADD obtained by abstracting the variables of cube from f,
  if successful; NULL otherwise.]

  SideEffects [None]

  SeeAlso     []

******************************************************************************/
DdNode *
cuddAddUnivAbstractRecur(
  DdManager * manager,
  DdNode * f,
  DdNode * cube)
{
    DdNode	*T, *E, *res, *res1, *res2, *one, *zero;

    statLine(manager);
    one = DD_ONE(manager);
    zero = DD_ZERO(manager);

    /* Cube is guaranteed to be a cube at this point.
    ** zero and one are the only constatnts c such that c*c=c.
    */
    if (f == zero || f == one || cube == one) {  
	return(f);
    }

    /* Abstract a variable that does not appear in f. */
    if (cuddI(manager,f->index) > cuddI(manager,cube->index)) {
	res1 = cuddAddUnivAbstractRecur(manager, f, cuddT(cube));
	if (res1 == NULL) return(NULL);
	cuddRef(res1);
	/* Use the "internal" procedure to be alerted in case of
	** dynamic reordering. If dynamic reordering occurs, we
	** have to abort the entire abstraction.
	*/
	res = cuddAddApplyRecur(manager, Cudd_addTimes, res1, res1);
	if (res == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    return(NULL);
	}
	cuddRef(res);
	Cudd_RecursiveDeref(manager,res1);
	cuddDeref(res);
	return(res);
    }

    if ((res = cuddCacheLookup2(manager, Cudd_addUnivAbstract, f, cube)) != NULL) {
	return(res);
    }

    T = cuddT(f);
    E = cuddE(f);

    /* If the two indices are the same, so are their levels. */
    if (f->index == cube->index) {
	res1 = cuddAddUnivAbstractRecur(manager, T, cuddT(cube));
	if (res1 == NULL) return(NULL);
        cuddRef(res1);
	res2 = cuddAddUnivAbstractRecur(manager, E, cuddT(cube));
	if (res2 == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    return(NULL);
	}
        cuddRef(res2);
	res = cuddAddApplyRecur(manager, Cudd_addTimes, res1, res2);
	if (res == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    Cudd_RecursiveDeref(manager,res2);
	    return(NULL);
	}
	cuddRef(res);
	Cudd_RecursiveDeref(manager,res1);
	Cudd_RecursiveDeref(manager,res2);
	cuddCacheInsert2(manager, Cudd_addUnivAbstract, f, cube, res);
	cuddDeref(res);
        return(res);
    } else { /* if (cuddI(manager,f->index) < cuddI(manager,cube->index)) */
	res1 = cuddAddUnivAbstractRecur(manager, T, cube);
	if (res1 == NULL) return(NULL);
        cuddRef(res1);
	res2 = cuddAddUnivAbstractRecur(manager, E, cube);
	if (res2 == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    return(NULL);
	}
        cuddRef(res2);
	res = (res1 == res2) ? res1 :
	    cuddUniqueInter(manager, (int) f->index, res1, res2);
	if (res == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    Cudd_RecursiveDeref(manager,res2);
	    return(NULL);
	}
	cuddDeref(res1);
	cuddDeref(res2);
	cuddCacheInsert2(manager, Cudd_addUnivAbstract, f, cube, res);
        return(res);
    }

} /* end of cuddAddUnivAbstractRecur */


/**Function********************************************************************

  Synopsis    [Performs the recursive step of Cudd_addOrAbstract.]

  Description [Performs the recursive step of Cudd_addOrAbstract.
  Returns the ADD obtained by abstracting the variables of cube from f,
  if successful; NULL otherwise.]

  SideEffects [None]

  SeeAlso     []

******************************************************************************/
DdNode *
cuddAddOrAbstractRecur(
  DdManager * manager,
  DdNode * f,
  DdNode * cube)
{
    DdNode	*T, *E, *res, *res1, *res2, *one;

    statLine(manager);
    one = DD_ONE(manager);

    /* Cube is guaranteed to be a cube at this point. */
    if (cuddIsConstant(f) || cube == one) {  
	return(f);
    }

    /* Abstract a variable that does not appear in f. */
    if (cuddI(manager,f->index) > cuddI(manager,cube->index)) {
	res = cuddAddOrAbstractRecur(manager, f, cuddT(cube));
	return(res);
    }

    if ((res = cuddCacheLookup2(manager, Cudd_addOrAbstract, f, cube)) != NULL) {
	return(res);
    }

    T = cuddT(f);
    E = cuddE(f);

    /* If the two indices are the same, so are their levels. */
    if (f->index == cube->index) {
	res1 = cuddAddOrAbstractRecur(manager, T, cuddT(cube));
	if (res1 == NULL) return(NULL);
        cuddRef(res1);
	if (res1 != one) {
	    res2 = cuddAddOrAbstractRecur(manager, E, cuddT(cube));
	    if (res2 == NULL) {
		Cudd_RecursiveDeref(manager,res1);
		return(NULL);
	    }
	    cuddRef(res2);
	    res = cuddAddApplyRecur(manager, Cudd_addOr, res1, res2);
	    if (res == NULL) {
		Cudd_RecursiveDeref(manager,res1);
		Cudd_RecursiveDeref(manager,res2);
		return(NULL);
	    }
	    cuddRef(res);
	    Cudd_RecursiveDeref(manager,res1);
	    Cudd_RecursiveDeref(manager,res2);
	} else {
	    res = res1;
	}
	cuddCacheInsert2(manager, Cudd_addOrAbstract, f, cube, res);
	cuddDeref(res);
        return(res);
    } else { /* if (cuddI(manager,f->index) < cuddI(manager,cube->index)) */
	res1 = cuddAddOrAbstractRecur(manager, T, cube);
	if (res1 == NULL) return(NULL);
        cuddRef(res1);
	res2 = cuddAddOrAbstractRecur(manager, E, cube);
	if (res2 == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    return(NULL);
	}
        cuddRef(res2);
	res = (res1 == res2) ? res1 :
	    cuddUniqueInter(manager, (int) f->index, res1, res2);
	if (res == NULL) {
	    Cudd_RecursiveDeref(manager,res1);
	    Cudd_RecursiveDeref(manager,res2);
	    return(NULL);
	}
	cuddDeref(res1);
	cuddDeref(res2);
	cuddCacheInsert2(manager, Cudd_addOrAbstract, f, cube, res);
        return(res);
    }

} /* end of cuddAddOrAbstractRecur */

/**Function********************************************************************

  Synopsis    [Performs the recursive step of Cudd_addMinAbstract.]

  Description [Performs the recursive step of Cudd_addMinAbstract.
  Returns the ADD obtained by abstracting the variables of cube from f,
  if successful; NULL otherwise.]

  SideEffects [None]

  SeeAlso     []

******************************************************************************/
DdNode *
cuddAddMinAbstractRecur(
  DdManager * manager,
  DdNode * f,
  DdNode * cube)
{
	DdNode	*T, *E, *res, *res1, *res2, *zero;

	zero = DD_ZERO(manager);

	/* Cube is guaranteed to be a cube at this point. */	
	if (f == zero || cuddIsConstant(cube)) {  
		return(f);
	}

	if (cuddI(manager,f->index) > cuddI(manager,cube->index)) {
		res = cuddAddMinAbstractRecur(manager, f, cuddT(cube));
		cuddRef(res);
		cuddDeref(res);
        	return(res);
	}

	if ((res = cuddCacheLookup2(manager, Cudd_addMinAbstract, f, cube)) != NULL) {
		return(res);
	}


	T = cuddT(f);
	E = cuddE(f);

	/* If the two indices are the same, so are their levels. */
	if (f->index == cube->index) {
		res1 = cuddAddMinAbstractRecur(manager, T, cuddT(cube));
		if (res1 == NULL) return(NULL);
        	cuddRef(res1);
		res2 = cuddAddMinAbstractRecur(manager, E, cuddT(cube));
		if (res2 == NULL) {
	    		Cudd_RecursiveDeref(manager,res1);
	    		return(NULL);
		}
		cuddRef(res2);
		res = cuddAddApplyRecur(manager, Cudd_addMinimum, res1, res2);
		if (res == NULL) {
	  		  Cudd_RecursiveDeref(manager,res1);
	   		 Cudd_RecursiveDeref(manager,res2);
	  		  return(NULL);
		}
		cuddRef(res);
		Cudd_RecursiveDeref(manager,res1);
		Cudd_RecursiveDeref(manager,res2);
		cuddCacheInsert2(manager, Cudd_addMinAbstract, f, cube, res);
		cuddDeref(res);
        	return(res);
    	}
	else { /* if (cuddI(manager,f->index) < cuddI(manager,cube->index)) */
		res1 = cuddAddMinAbstractRecur(manager, T, cube);
		if (res1 == NULL) return(NULL);
        	cuddRef(res1);
		res2 = cuddAddMinAbstractRecur(manager, E, cube);
		if (res2 == NULL) {
			Cudd_RecursiveDeref(manager,res1);
			return(NULL);
		}
        	cuddRef(res2);
		res = (res1 == res2) ? res1 :
		    cuddUniqueInter(manager, (int) f->index, res1, res2);
		if (res == NULL) {
	   		 Cudd_RecursiveDeref(manager,res1);
	    		Cudd_RecursiveDeref(manager,res2);
	    		return(NULL);
		}
		cuddDeref(res1);
		cuddDeref(res2);
		cuddCacheInsert2(manager, Cudd_addMinAbstract, f, cube, res);
        	return(res);
	}
	    
} /* end of cuddAddMinAbstractRecur */


/**Function********************************************************************

  Synopsis    [Performs the recursive step of Cudd_addMaxAbstract.]

  Description [Performs the recursive step of Cudd_addMaxAbstract.
  Returns the ADD obtained by abstracting the variables of cube from f,
  if successful; NULL otherwise.]

  SideEffects [None]

  SeeAlso     []

******************************************************************************/
DdNode *
cuddAddMaxAbstractRecur(
  DdManager * manager,
  DdNode * f,
  DdNode * cube)
{
	DdNode	*T, *E, *res, *res1, *res2, *zero;

	zero = DD_ZERO(manager);

	/* Cube is guaranteed to be a cube at this point. */	
	if (f == zero || cuddIsConstant(cube)) {  
		return(f);
	}

	if (cuddI(manager,f->index) > cuddI(manager,cube->index)) {
		res = cuddAddMaxAbstractRecur(manager, f, cuddT(cube));
		cuddRef(res);
		cuddDeref(res);
        	return(res);
	}

	if ((res = cuddCacheLookup2(manager, Cudd_addMaxAbstract, f, cube)) != NULL) {
		return(res);
	}


	T = cuddT(f);
	E = cuddE(f);

	/* If the two indices are the same, so are their levels. */
	if (f->index == cube->index) {
		res1 = cuddAddMaxAbstractRecur(manager, T, cuddT(cube));
		if (res1 == NULL) return(NULL);
        	cuddRef(res1);
		res2 = cuddAddMaxAbstractRecur(manager, E, cuddT(cube));
		if (res2 == NULL) {
	    		Cudd_RecursiveDeref(manager,res1);
	    		return(NULL);
		}
		cuddRef(res2);
		res = cuddAddApplyRecur(manager, Cudd_addMaximum, res1, res2);
		if (res == NULL) {
	  		  Cudd_RecursiveDeref(manager,res1);
	   		 Cudd_RecursiveDeref(manager,res2);
	  		  return(NULL);
		}
		cuddRef(res);
		Cudd_RecursiveDeref(manager,res1);
		Cudd_RecursiveDeref(manager,res2);
		cuddCacheInsert2(manager, Cudd_addMaxAbstract, f, cube, res);
		cuddDeref(res);
        	return(res);
    	}
	else { /* if (cuddI(manager,f->index) < cuddI(manager,cube->index)) */
		res1 = cuddAddMaxAbstractRecur(manager, T, cube);
		if (res1 == NULL) return(NULL);
        	cuddRef(res1);
		res2 = cuddAddMaxAbstractRecur(manager, E, cube);
		if (res2 == NULL) {
			Cudd_RecursiveDeref(manager,res1);
			return(NULL);
		}
        	cuddRef(res2);
		res = (res1 == res2) ? res1 :
		    cuddUniqueInter(manager, (int) f->index, res1, res2);
		if (res == NULL) {
	   		 Cudd_RecursiveDeref(manager,res1);
	    		Cudd_RecursiveDeref(manager,res2);
	    		return(NULL);
		}
		cuddDeref(res1);
		cuddDeref(res2);
		cuddCacheInsert2(manager, Cudd_addMaxAbstract, f, cube, res);
        	return(res);
	}	    

} /* end of cuddAddMaxAbstractRecur */


/*---------------------------------------------------------------------------*/
/* Definition of static functions                                            */
/*---------------------------------------------------------------------------*/


/**Function********************************************************************

  Synopsis    [Checks whether cube is an ADD representing the product
  of positive literals.]

  Description [Checks whether cube is an ADD representing the product of
  positive literals. Returns 1 in case of success; 0 otherwise.]

  SideEffects [None]

  SeeAlso     []

******************************************************************************/
static int
addCheckPositiveCube(
  DdManager * manager,
  DdNode * cube)
{
    if (Cudd_IsComplement(cube)) return(0);
    if (cube == DD_ONE(manager)) return(1);
    if (cuddIsConstant(cube)) return(0);
    if (cuddE(cube) == DD_ZERO(manager)) {
        return(addCheckPositiveCube(manager, cuddT(cube)));
    }
    return(0);

} /* end of addCheckPositiveCube */
