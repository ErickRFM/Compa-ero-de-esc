package org.companerodeescuela.feature.admin

import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.shared.contracts.*

/** Operation description only; passwords, OTPs and proof bytes never enter UI state. */
data class AdminOperation(val operationId:String,val targetId:String,val action:StepUpAction,val targetName:String)
data class AdminUiState(val loading:Boolean=true,val working:Boolean=false,
    val overview:AdministrationOverview?=null,val groups:List<AcademicGroupSummary> = emptyList(),
    val assignments:List<TutorAssignmentSummary> = emptyList(),val operation:AdminOperation?=null,
    val failure:AppError?=null,val groupFailure:AppError?=null,val assignmentFailure:AppError?=null,
    val completedOperationId:String?=null,val ownerKey:String?=null)
