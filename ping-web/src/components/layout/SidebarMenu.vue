<template>
  <template v-for="item in visibleItems" :key="item.id">
    <el-sub-menu v-if="item.children && item.children.length" :index="item.path || `dir-${item.id}`">
      <template #title>
        <el-icon><component :is="iconOf(item)" /></el-icon>
        <span>{{ item.name }}</span>
      </template>
      <SidebarMenu :items="item.children" />
    </el-sub-menu>
    <el-menu-item v-else-if="item.path" :index="item.path">
      <el-icon><component :is="iconOf(item)" /></el-icon>
      <span>{{ item.name }}</span>
    </el-menu-item>
  </template>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] }
})

function iconOf(item) {
  return item.icon || 'Menu'
}

// 过滤隐藏菜单和空目录（目录下没有可见子菜单时不展示）
function filterMenu(list) {
  return (list || [])
    .filter(item => item.visible !== 0)
    .map(item => ({ ...item, children: filterMenu(item.children) }))
    .filter(item => (item.children && item.children.length) || item.path)
}

const visibleItems = computed(() => filterMenu(props.items))
</script>
