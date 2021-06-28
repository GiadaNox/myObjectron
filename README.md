# myObjectron

OBJECTRON AAR / Native Library Guide

1. Overview

    Google Mediapipe의 3D 물체인식 module Objectron을 Android에 사용하기 위한 Project
  
    Mediapipe GPU Pipeline : 
  
    GPU에서 Tensorflow Lite을 사용해 3D Object Detection을 실행하는 Mediapipe Graph로,
    
    Mediapipe Binary Graph 가 빌드 된 안드로이드 용 AAR 파일과 물체 인식을 수행하는 tensorflow lite 모델, 
    
    인식한 모델의 ID와 물체 명을 연결하는 Label Map, Computer Vision 오픈 라이브러리 openCV 를 이용하여 
    
    카메라로 들어오는 Input Video를 입력으로 받아 3D Object Detection을 수행한 후 결과를 Output video와 인식 결과를 표현하는 Bounding Box로 반환한다.
    

2. Major Implementations
  Google Mediapipe에서 물체 인식을 위해 제공하는 Specification File 혹은, Android 용으로 Build된 File
  reference : https://github.com/GiadaNox/mediapipe/tree/master/mediapipe/examples/android/src/java/com/google/mediapipe/apps/objectdetection3d
  1)app/libs/mediapipe_objectron.aar
    Mediapipe가 물체인식을 하기 위해 필요한 Mobile 용 Calculator (계산에 필요한 class 및 function  으로 구성된 .cc , .proto 파일) 를 Android 용으로 bazel 빌드한 것
  2)app/src/main/assets/mobile_gpu_binary_graph.binarypb
    Mediapipe 의 Data Pipeline Manager 역할을 하는 Native Library. 이 파일이 카메라로 부터 Input Video 영상을 받아 Object Detection 을 수행하고 결과를 반환하도록 한다.
  3)app/src/main/assets/object_detection_ssd_mobilenetv2_oidv4_fp16.tflite
    app/src/main/assets/object_detection_3d_sneakers.tflite
    Mediapipe의 Object Detection 을 수행하는 Core Model. 인식 결과를 물체 ID로 반환
  4)app/src/main/assets/object_detection_oidv4_labelmap.txt
    위의 Tensorflow Lite 모델이 인식한 물체ID를 이름으로 변경할 수 있도록 Mapping을 제공
  5)app/src/main/assets/texture.jpg
    app/src/main/assets/model.obj.uuu
    app/src/main/assets/classic_colors.png
    app/src/main/assets/box.obj.uuu
    물체 인식 결과의 시각적 특징을 결정하는 요소
  6)app/src/main/jniLibs/arm64-v8a/libopencv_java3.so
    app/src/main/jniLibs/armeabi-v7a/libopencv_java3.so
    Java 용 OpenCV
    
3. Input Data Flow
  reference : https://github.com/GiadaNox/myObjectron/blob/main/app/src/main/java/com/example/myobjectron/MainActivity.java
  Binary Graph는 Input Video와 aar파일, 모델, specification 정보를 사용하여 물체 인식을 수행하고, 결과로 bounding box가 포함된 Output Video를 제공한다.
  Binary Graph : mobile_gpu_binary_graph.binarypb
  Graph 로 들어가는 Input Video Stream : "input_video"
  Graph 에서 나오는 Output Video Stream : "output_video"
  ** Binary Graph 가 해당 이름을 사용하므로, 반드시 변수 이름은 "input_video", "output_video"로 설정해야 함.
  그 외에 input side packet에 넣어 물체 인식 및 결과 visualization을 위해 사용하는 정보 : "texture.jpg", "model.obj.uuu", "classic_colors.png", "box.obj.uuu"

  아래는 Input Video부터 진행되는 data flow로, 작업은 크게 물체 인식을 수행하는 Objectron GPU Subgraph와 인식한 물체의 움직임을 tracking하는 Objectron Tracking GPU Subgraph 로 나뉜다. 
  Objectron GPU Subgraph는 물체 인식 core 엔진인 Objectron OBject Detection GPU Subgraph와 인식 결과를 박스로 표시해주는 Box Landmark Localization GPU Subgraph로 구성되며, 
  각 Subgraph 의 input/output stream 정보는 아래와 같다. 
  
  각 Subgraph에 사용되는 Calculator 정보는 아래 BUILD파일에서 확인할 수 있다. 
  https://github.com/GiadaNox/mediapipe/blob/master/mediapipe/modules/objectron/BUILD

  1)Objectron GPU Subgraph
  (아래 Objectron Object Detection GPU Subgraph, Box Landmark Localization GPU Subgraph 를 포함) 
  https://github.com/GiadaNox/mediapipe/blob/master/mediapipe/modules/objectron/objectron_gpu.pbtxt
  input : "IMAGE_GPU:image"
  input_side_packet:"LABELS_CSV:allowed_labels"
  input_side_packet:"MAX_NUM_OBJECTS:max_num_objects"
  output:"FRAME_ANNOTATION:detected_objects"

    a)Objectron Object Detection GPU Subgraph
      tensorflow lite 모델과 mapping 정보를 사용하여 물체 인식 후, 최대 인식 가능 갯수와 인식 threshold에 따라 물체 인식 결과를 반환한다. 
      https://github.com/GiadaNox/mediapipe/blob/master/mediapipe/modules/objectron/object_detection_oid_v4_gpu.pbtxt
      input : "IMAGE_GPU:input_video"
      ** 입력 input video 은 기본적으로 unsigned char 로 되어 있어, image format 은 channel 수에만 영향을 받는다.
      input_side_packet : "LABEDLS_CSV:allowed_labels"
      output : "DETECTIONS:detections"
      사용하는 물체 인식 모델 : object_detection_oidv4_labelmap.txt
      사용하는 매핑 정보 : object_detection_ssd_mobilenetv2_oidv4_fp16.tflite

    b)Box Landmark Localization GPU Subgraph
      tensorflow lite 모델을 실행하여 물체 인식 box/score/key points를 결과로 받는다
      https://github.com/GiadaNox/mediapipe/blob/master/mediapipe/modules/objectron/box_landmark_gpu.pbtxt
      input : "IMAGE:image"
      input : "NORM_RECT:box_rect"
      output : "NORM_LANDMARKS:box_landmarks"
      사용하는 모델 : object_detection_3d.tflite

  2)Objectron Tracking GPU Subgraph
    https://github.com/GiadaNox/mediapipe/blob/master/mediapipe/modules/objectron/objectron_tracking_1stage_gpu.pbtxt
    input : "FRAME_ANNOTATION:objects"
    input : "IMAGE_GPU:input_video"
    output : "LIFTED_FRAME_ANNOTATION:lifted_tracked_objects"

 4. Android Studio 를 사용하여 Mediapipe 실행하는 방법
  reference : https://github.com/GiadaNox/myObjectron/blob/main/app/src/main/java/com/example/myobjectron/MainActivity.java
  1)필요한 Components
      a)com.google.mediapipe.components
        CameraHelper
        CameraXPreviewHelper : CameraX를 통한 camera access 관리
        ExternalTextureConverter : GL_TEXTURE_EXTERNAL_OES 를 android camera 에서 regular texture로 바꾸어 frame processore와 mediapipe graph에서 사용될 수 있도록 함
        FrameProcessor : camera-preview frames을 mediapipe graph에 보내 처리하고 처리된 frames을 Surface에 display. components.TextureFrameProcessor, components.AudioDataProcessor 로 구성되어 mediapipe graph에 데이터를 송신
        PermissionHelper
      b)com.google.mediapipe.framework
        AndroidAssetUtil
      c)com.google.mediapipe.glutil
        EglManager : EGLContext 생성 및 관리
      d)android.graphics.SurfaceTexture : camera-preview frames가 있는 장소
      e)android.view.SurfaceView : camera-preview frames 이 mediapipe graph에 의해 처리

  2)작업 실행 순서
      Step 1. 화면 그리기
        a) 화면에 res > layout > activity_main.xml 파싱하여 View 그리기
        b) 새로운 SurefaceView 생성하여 View 정보를 Processor, Converter에 전달
      Step 2. Binary Graph가 App Asset에 접근할 수 있도록 Manager 설정
        a) Native Asset Manager 시작
        b) 새로운 EglManager 시작
        c) 새로운 Frame Processor 생성
          Frame Processor 에 아래 정보를 전달
          Android Context
          parent Native Context
          BINARY_GRAPH_NAME = mobile_gpu.binarypb //Graph의 binary representation을 포함하는 파일 이름
          INPUT_VIDEO_STREAM_NAME = "input_video" //input video frame을 받을 Graph input stream 
          OUTPUT_VIDEO_STREAM_NAME = "output_video" // outpu frame이 생성될 output stream
          d) Processor 설정
          Processor에 들어온 SurfaceView 결과 전달 후, 상하반전
          ** Camera Preview Frame을 상하 반전 시켜 Processor에 전달하여야 한다.
          OpenGL 이 이미지의 origin이 좌하단에 있다고 가정하는 것과 달리, Mediapipe는 좌상단에 있다고 가정하고 처리하기 때문.
      Step3. cameraPermission 확인
      Step4. 새로운 converter 생성
        a) converter 상하반전
        b) converter가 processor를 사용하도록 설정
      Step 5. 카메라 켜기 (permission grant necessary)
        a) 후면 카메라 켜기
