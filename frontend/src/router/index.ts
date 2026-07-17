import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'

import ProjectHomeView from '@/views/ProjectHomeView.vue'
import NewProjectView from '@/views/NewProjectView.vue'
import ProjectWorkspace from '@/layouts/ProjectWorkspace.vue'
import ProjectModulePlaceholder from '@/views/ProjectModulePlaceholder.vue'

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  return createRouter({
    history,
    routes: [
      {
        path: '/',
        name: 'project-home',
        component: ProjectHomeView,
      },
      {
        path: '/projects/new',
        name: 'new-project',
        component: NewProjectView,
      },
      {
        path: '/projects/:projectId',
        component: ProjectWorkspace,
        children: [
          {
            path: '',
            redirect: (to) => ({
              name: 'project-overview',
              params: { projectId: to.params.projectId },
            }),
          },
          {
            path: 'overview',
            name: 'project-overview',
            component: ProjectModulePlaceholder,
          },
          {
            path: 'questions',
            name: 'project-questions',
            component: ProjectModulePlaceholder,
          },
          {
            path: 'requirements',
            name: 'project-requirements',
            component: ProjectModulePlaceholder,
          },
          {
            path: 'architecture',
            name: 'project-architecture',
            component: ProjectModulePlaceholder,
          },
          {
            path: 'flowchart',
            name: 'project-flowchart',
            component: ProjectModulePlaceholder,
          },
          {
            path: 'prd',
            name: 'project-prd',
            component: ProjectModulePlaceholder,
          },
        ],
      },
    ],
  })
}

export const router = createAppRouter()
