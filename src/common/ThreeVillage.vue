<script setup>
import * as THREE from 'three'
import { onMounted } from 'vue'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls'

onMounted(() => {
  // 🚀 1. Three.js 기본 설정
  const scene = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000)
  camera.position.set(8, 3, 5)

  const renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(window.innerWidth, window.innerHeight - 65, false)
  document.body.appendChild(renderer.domElement)

  const controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true // 부드러운 감속 효과
  // controls.dampingFactor = 0.05 // 감속 정도
  // controls.rotateSpeed = 1 // 회전 속도
  // controls.zoomSpeed = 1.2 // 줌 속도

  // 🌅 2. 배경을 푸른 하늘 색으로 설정
  scene.background = new THREE.Color(0x87ceeb) // 밝은 하늘색

  // 땅 추가
  const textureLoader = new THREE.TextureLoader()
  const groundTexture = textureLoader.load('/assets/textures/grasslight-big.jpg')

  // 텍스처 반복 설정 (타일처럼 보이도록)
  groundTexture.wrapS = THREE.RepeatWrapping
  groundTexture.wrapT = THREE.RepeatWrapping
  groundTexture.repeat.set(10, 10)

  // 머티리얼 생성 (텍스처 적용)
  const groundMaterial = new THREE.MeshStandardMaterial({
    map: groundTexture,
    side: THREE.DoubleSide,
  })

  const ground = new THREE.Mesh(new THREE.PlaneGeometry(100, 100), groundMaterial)
  ground.rotation.x = -Math.PI / 2
  ground.receiveShadow = true
  renderer.shadowMap.enabled = true
  scene.add(ground)

  // 💡 3. 조명 추가 (더 밝게 설정)
  // 🌞 Directional Light (태양광)
  const sunLight = new THREE.DirectionalLight(0xffffff, 1.2)
  sunLight.position.set(5, 10, 5)
  sunLight.castShadow = true
  scene.add(sunLight)

  // 🌟 Ambient Light (환경광 - 전체적으로 밝게)
  const ambientLight = new THREE.AmbientLight(0xffffff, 0.5)
  scene.add(ambientLight)

  // 🔥 Point Light (강한 점광원 - 특정 위치 강조)
  const pointLight = new THREE.PointLight(0xffaa55, 1.5, 10)
  pointLight.position.set(2, 3, 2)
  scene.add(pointLight)

  // 🌈 Hemisphere Light (반구광 - 하늘+땅 조화)
  const hemiLight = new THREE.HemisphereLight(0x87ceeb, 0x3d2b1f, 0.8)
  scene.add(hemiLight)

  // 🎨 GLB 파일 로드
  const gltfLoader = new GLTFLoader()
  let model, mixer
  // const moveSpeed = 0.1
  const rotationSpeed = 0.05
  const keyStates = {}
  let velocity = 0 // 현재 속도

  const acceleration = 0.002 // 가속도
  const maxSpeed = 1 // 최대 속도
  const friction = 0.98 // 감속 계수 (0~1 사이 값)

  let isJumping = false
  let jumpVelocity = 0
  const gravity = 0.01 // 중력
  const jumpPower = 0.2 // 점프 힘

  gltfLoader.load('/assets/threeEssets/v8-chopper1.glb', (gltf) => {
    model = gltf.scene
    model.scale.set(0.5, 0.5, 0.5)
    model.position.set(0, 0, 0)
    scene.add(model)
    if (gltf.animations.length > 0) {
      mixer = new THREE.AnimationMixer(model)
      gltf.animations.forEach((animation) => {
        const action = mixer.clipAction(animation)
        action.play()
      })
    }
  })

  // 🔄 반응형 처리
  function onWindowResize() {
    camera.aspect = window.innerWidth / window.innerHeight
    camera.updateProjectionMatrix()
    renderer.setSize(window.innerWidth, window.innerHeight - 65, false)
  }
  window.addEventListener('resize', onWindowResize)

  // 🔹 키 입력 감지
  window.addEventListener('keydown', (event) => {
    keyStates[event.code] = true

    if (event.code === 'Space' && !isJumping) {
      isJumping = true
      jumpVelocity = jumpPower // 점프 힘 적용
    }
  })

  window.addEventListener('keyup', (event) => {
    keyStates[event.code] = false
  })

  // 🚀 바이크 이동 처리
  function updateMovement() {
    if (!model) return

    const moveDirection = new THREE.Vector3()
    const rotationMatrix = new THREE.Matrix4()
    rotationMatrix.extractRotation(model.matrixWorld)

    // 🔹 가속 및 감속
    if (keyStates['ArrowUp'] || keyStates['KeyW']) {
      velocity = Math.max(velocity - acceleration, -maxSpeed / 2) // 후진 속도는 절반
    } else if (keyStates['ArrowDown'] || keyStates['KeyS']) {
      velocity = Math.min(velocity + acceleration, maxSpeed) // 최대 속도 제한
    } else {
      velocity *= friction // 키를 놓으면 점진적 감속
      if (Math.abs(velocity) < 0.001) velocity = 0 // 너무 느려지면 정지
    }

    // 🔹 브레이크 (Q 키)
    if (keyStates['KeyQ']) {
      velocity *= 0.85 // 브레이크 감속
      if (Math.abs(velocity) < 0.002) velocity = 0 // 거의 멈추면 정지
    }

    // 🔹 회전
    if (keyStates['ArrowLeft'] || keyStates['KeyA']) {
      model.rotation.y += rotationSpeed
    }
    if (keyStates['ArrowRight'] || keyStates['KeyD']) {
      model.rotation.y -= rotationSpeed
    }

    // 🔹 점프
    if (isJumping) {
      model.position.y += jumpVelocity // 점프 속도 적용
      jumpVelocity -= gravity // 중력 적용

      // 땅에 닿으면 착지
      if (model.position.y <= 0) {
        model.position.y = 0
        isJumping = false
        jumpVelocity = 0
      }
    }

    // 🔹 속도를 적용한 이동
    moveDirection.z = velocity
    moveDirection.applyMatrix4(rotationMatrix)
    model.position.add(moveDirection)
  }

  // 🎥 카메라 따라가기
  function updateCamera() {
    if (!model) return

    const offset = new THREE.Vector3(0, 4, 8) // 바이크 뒤쪽 위치
    offset.applyMatrix4(model.matrixWorld) // 모델의 회전 반영

    camera.position.lerp(offset, 0.1) // 부드럽게 이동
    camera.lookAt(model.position) // 모델을 바라보게 설정
  }

  function updateAnimation() {
    if (!mixer) return

    const isMoving =
      keyStates['ArrowUp'] || keyStates['KeyW'] || keyStates['ArrowDown'] || keyStates['KeyS']

    mixer._actions.forEach((action) => {
      if (isMoving) {
        action.play() // 이동 중이면 애니메이션 실행
      } else {
        action.stop() // 멈추면 애니메이션 정지
      }
    })
  }

  // 🌀 애니메이션 루프
  const clock = new THREE.Clock()
  function animate() {
    requestAnimationFrame(animate)
    controls.update() // 카메라 조작 적용
    updateMovement() // 키보드 입력에 따라 바이크 이동
    updateAnimation() // 애니메이션 실행 여부 결정
    updateCamera()
    if (mixer) mixer.update(clock.getDelta())
    renderer.render(scene, camera)
  }
  animate()
})
</script>

<template>
  <main>
    <div></div>
  </main>
</template>
