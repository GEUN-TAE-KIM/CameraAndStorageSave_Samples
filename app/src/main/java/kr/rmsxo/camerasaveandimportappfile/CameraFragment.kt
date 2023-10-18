package kr.rmsxo.camerasaveandimportappfile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import kr.rmsxo.camerasaveandimportappfile.databinding.DialogImageBinding
import kr.rmsxo.camerasaveandimportappfile.databinding.FragmentCameraBinding
import kr.rmsxo.camerasaveandimportappfile.util.BaseFragment
import java.util.Locale

class CameraFragment : BaseFragment<FragmentCameraBinding>(R.layout.fragment_camera) {
    enum class CameraState {
        CAMERA1,
        CAMERA2,
    }

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var storageLauncher: ActivityResultLauncher<Intent>
    private lateinit var storageLauncher2: ActivityResultLauncher<Intent>
    private var currentPhotoUri: Uri? = null
    private var currentPhotoUri2: Uri? = null

    private var currentCameraState: CameraState = CameraState.CAMERA1
    // 카메라 앱이 종료되었는지 여부를 추적하는 변수 추가
    private var isCameraAppClosed = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraResult()
        setButton()
    }

    private fun cameraResult() {
        cameraLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    isCameraAppClosed = false
                    when (currentCameraState) {
                        CameraState.CAMERA1 -> {
                            currentPhotoUri?.let { uri ->
                                handleButtonUpload(
                                    binding.buttonFileUploadEnd,
                                    binding.buttonFileUploadDelete,
                                    uri
                                )
                                isCameraAppClosed = true
                            }
                        }

                        CameraState.CAMERA2 -> {
                            currentPhotoUri2?.let {uri ->
                                handleButtonUpload(
                                    binding.buttonFileUploadEnd2,
                                    binding.buttonFileUploadDelete2,
                                    uri
                                )
                                isCameraAppClosed = true
                            }
                        }
                    }
                } else {
                    isCameraAppClosed = true
                }
            }

        storageLauncher = registerLauncher {
            handleStorageResult(
                it,
                binding.buttonFileUploadEnd,
                binding.buttonFileUploadDelete,
                binding.error
            ) { uri -> currentPhotoUri = uri }
        }
        storageLauncher2 = registerLauncher {
            handleStorageResult(
                it,
                binding.buttonFileUploadEnd2,
                binding.buttonFileUploadDelete2,
                binding.error2
            ) { uri -> currentPhotoUri2 = uri }
        }

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    launchCamera { uri -> currentPhotoUri = uri }
                }
            }
    }

    // 일을 선택하거나 다른 Activity에서 결과를 받기
    private fun registerLauncher(callback: (Uri?) -> Unit) =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                callback(uri)
            }
        }

    // 파일의 Uri와 관련된 여러 UI 컴포넌트 (Button과 TextView)의 상태를 처리하며 또한 파일의 확장자와 크기를 검사하여 에러 메시지를 표시 함
    private fun handleStorageResult(
        uri: Uri?,
        buttonEnd: Button,
        buttonDelete: Button,
        errorView: TextView,
        updateUri: (Uri) -> Unit
    ) {
        uri?.let {
            val extension = getFileName(it).substringAfterLast('.', "").lowercase(Locale.ROOT)
            val fileSize =
                requireContext().contentResolver.openFileDescriptor(it, "r")?.statSize ?: 0

            when {
                !listOf("jpg", "jpeg").contains(extension) -> {
                    errorView.visibility = View.VISIBLE
                    errorView.text = "형식이 틀립니다"
                    return
                }

                // 크기 -> byte
                fileSize > 300000 -> {
                    errorView.visibility = View.VISIBLE
                    errorView.text = "파일 크기가 초과 했습니다"
                    return
                }

                else -> {
                    errorView.visibility = View.GONE
                    buttonEnd.text = getFileName(it)
                    buttonEnd.visibility = View.VISIBLE
                    buttonDelete.visibility = View.VISIBLE
                    updateUri(it)
                }
            }
        }
    }

    private fun setButton() {

        // 카메라 실행
        binding.buttonCamera.setOnClickListener {
            currentCameraState = CameraState.CAMERA1
            requestCameraPermission { launchCamera { uri -> currentPhotoUri = uri } }
        }

        // 파일 업로드 선택
        binding.buttonFile.setOnClickListener {
            val storageIntent = Intent(Intent.ACTION_GET_CONTENT)
            storageIntent.type = "image/Pictures/*"
            storageLauncher.launch(storageIntent)
        }

        // 미리보기
        binding.buttonFileUploadEnd.setOnClickListener {
            handleButtonUpload(
                binding.buttonFileUploadEnd,
                binding.buttonFileUploadDelete,
                currentPhotoUri
            )
        }

        // 삭제
        binding.buttonFileUploadDelete.setOnClickListener {

            // UI에서 파일 이름을 지웁니다.
            binding.buttonFileUploadEnd.text = ""
            binding.buttonFileUploadEnd.visibility = View.GONE
            binding.buttonFileUploadDelete.visibility = View.GONE

            // 현재 Photo Uri 초기화
            currentPhotoUri = null
        }

        binding.buttonFileUploadEnd2.setOnClickListener {
            handleButtonUpload(
                binding.buttonFileUploadEnd2,
                binding.buttonFileUploadDelete2,
                currentPhotoUri2
            )
        }

        binding.buttonCamera2.setOnClickListener {
            currentCameraState = CameraState.CAMERA2
            requestCameraPermission { launchCamera { uri -> currentPhotoUri2 = uri } }
        }

        binding.buttonFile2.setOnClickListener {
            val storageIntent = Intent(Intent.ACTION_GET_CONTENT)
            storageIntent.type = "image/Pictures/*"
            storageLauncher2.launch(storageIntent)
        }

        binding.buttonFileUploadDelete2.setOnClickListener {
            binding.buttonFileUploadEnd2.text = ""
            binding.buttonFileUploadEnd2.visibility = View.GONE
            binding.buttonFileUploadDelete2.visibility = View.GONE

            currentPhotoUri2 = null
        }


    }

    private fun handleButtonUpload(buttonEnd: Button, buttonDelete: Button, uri: Uri?) {
        uri?.let {
            if (isCameraAppClosed) {  // 카메라 앱이 종료되지 않았을 때만 미리보기 표시
                showDialogImage(it)
            }
            // 데이터 베이스 저장 별도 처리 필요
        }
        buttonEnd.text = uri?.let { getFileName(it) }
        buttonEnd.visibility = View.VISIBLE
        buttonDelete.visibility = View.VISIBLE
    }

    // 카메라 권한
    private fun requestCameraPermission(callback: () -> Unit) {
        val permission = Manifest.permission.CAMERA
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                callback()
            }

            // 카메라 권한 거절로 되어 있을 시
            shouldShowRequestPermissionRationale(permission) -> {
                AlertDialog.Builder(requireContext())
                    .setMessage("카메라 권한이 필요합니다.")
                    .setPositiveButton("확인") { _, _ ->
                        permissionLauncher.launch(permission)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }

            else -> {
                permissionLauncher.launch(permission)
            }
        }
    }

    // 카메라 실행 지정된 uri에 저장
    private fun launchCamera(updateUri: (Uri) -> Unit) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "JPEG_${System.currentTimeMillis()}_")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        uri?.let {
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            cameraLauncher.launch(cameraIntent)
            updateUri(uri)
        }
    }

    // 파일 이름 가져오기
    private fun getFileName(uri: Uri): String {
        var name = ""
        val returnCursor = requireContext().contentResolver.query(uri, null, null, null, null)
        returnCursor?.let { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            name = cursor.getString(nameIndex)
            cursor.close()
        }
        return name
    }

    // 미리보기 다이얼로그
    private fun showDialogImage(uri: Uri) {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(requireContext())
        val dialogBinding = DialogImageBinding.inflate(inflater)

        Glide.with(this).load(uri).into(dialogBinding.dialogImageView)

        builder.setView(dialogBinding.root)
        builder.setPositiveButton("확인", null)
        builder.show()


    }


}
