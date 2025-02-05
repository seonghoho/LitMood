<script setup>
import * as THREE from 'three'
import { onMounted } from 'vue'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls'

// import chopper from '@/assets/threeEssets/v8-chopper.fbx'

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
  controls.dampingFactor = 0.05 // 감속 정도
  controls.rotateSpeed = 1 // 회전 속도
  controls.zoomSpeed = 1.2 // 줌 속도

  // 🌅 2. 배경을 푸른 하늘 색으로 설정
  scene.background = new THREE.Color(0x87ceeb) // 밝은 하늘색

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
  let mixer
  gltfLoader.load('/assets/threeEssets/v8-chopper1.glb', (gltf) => {
    const model = gltf.scene
    model.scale.set(0.5, 0.5, 0.5)
    model.position.set(0, 0, 0)
    scene.add(model)
    gltf.scene.traverse((child) => {
      if (child.isMesh && !child.material) {
        child.material = new THREE.MeshStandardMaterial({ color: 0xff0000 }) // 빨간색 적용
      }
    })

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

  // 🌀 애니메이션 루프
  const clock = new THREE.Clock()
  function animate() {
    requestAnimationFrame(animate)
    controls.update() // 카메라 조작 적용
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
