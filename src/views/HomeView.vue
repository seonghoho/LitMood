<script setup>
import * as THREE from 'three'

const scene = new THREE.Scene()
// 시야각 (해당 시점의 화면이 보여지는 정도)
// 종횡비 (요소의 높이와 너비에 맞추어 표시하도록 한다.)
// near, far 절단면 (far 보다 멀리있거나 near보다 가까운 오브젝트는 렌더링되지 않는다.)
const camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000)
camera.position.z = 5

// renderer 인스턴스 생성과 동시에, 렌더링할 곳의 크기를 설정해줘야 한다.
// setSize의 세 번째 인자인 UpdateStyle을 false로 두면 낮은 해상도로 렌더링한다.
const renderer = new THREE.WebGLRenderer()
renderer.setSize(window.innerWidth, window.innerHeight - 65, false)
document.body.appendChild(renderer.domElement)

const geometry = new THREE.BoxGeometry(1, 1, 1)
const material = new THREE.MeshBasicMaterial({ color: 0x00ff00 })
const cube = new THREE.Mesh(geometry, material)
scene.add(cube)

function animate() {
  requestAnimationFrame(animate)
  cube.rotation.x += 0.01
  cube.rotation.y += 0.01
  renderer.render(scene, camera)
}
animate()

///////////////////////////////////////////////////////////////////////////////////////////////////
// const scene = new THREE.Scene()

// // Line 긋기
// const camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 1, 500)
// camera.position.set(0, 0, 100)
// camera.lookAt(0, 0, 0)
// const renderer = new THREE.WebGLRenderer()
// renderer.setSize(window.innerWidth, window.innerHeight - 65, false)
// document.body.appendChild(renderer.domElement)

// //create a blue LineBasicMaterial
// const materialLine = new THREE.LineBasicMaterial({ color: 0x0000ff })
// //꼭짓점에 대한 기하학을 정의
// const points = []
// points.push(new THREE.Vector3(-10, 0, 0))
// points.push(new THREE.Vector3(0, 10, 0))
// points.push(new THREE.Vector3(10, 0, 0))
// const geometryLine = new THREE.BufferGeometry().setFromPoints(points)
// const line = new THREE.Line(geometryLine, materialLine)

// scene.add(line)

// function animate() {
//   requestAnimationFrame(animate)
//   renderer.render(scene, camera)
// }
// animate()
</script>

<template>
  <main>
    <!-- three.js 연습 -->
    <div></div>
  </main>
</template>
