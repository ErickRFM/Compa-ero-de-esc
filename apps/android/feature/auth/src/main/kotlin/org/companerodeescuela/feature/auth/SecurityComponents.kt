package org.companerodeescuela.feature.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import org.companerodeescuela.core.common.result.AppError

@Composable internal fun SecurityFailure(error:AppError) {
    val resource=when(error) {
        is AppError.Network -> R.string.security_error_network
        is AppError.Http -> when(error.status) {
            401 -> R.string.security_error_session
            403 -> R.string.security_error_denied
            409 -> R.string.security_error_conflict
            422 -> R.string.security_error_validation
            429 -> R.string.security_error_rate
            in 500..599 -> R.string.security_error_unavailable
            else -> R.string.security_error_generic
        }
        else -> R.string.security_error_generic
    }
    Text(stringResource(resource),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("security_error"))
}
@Composable internal fun SecurityMemoryField(value:String,onChange:(String)->Unit,label:Int,tag:String,enabled:Boolean=true,password:Boolean=false,otp:Boolean=false) {
    OutlinedTextField(value,onChange,modifier=Modifier.fillMaxWidth().testTag(tag),enabled=enabled,singleLine=true,
        label={ Text(stringResource(label)) },visualTransformation=if(password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions=KeyboardOptions(keyboardType=if(password) KeyboardType.Password else if(otp) KeyboardType.NumberPassword else KeyboardType.Text),colors=institutionalFieldColors())
}
