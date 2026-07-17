import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'

import ProjectHomeView from '@/views/ProjectHomeView.vue'
import NewProjectView from '@/views/NewProjectView.vue'
import ProjectWorkspace from '@/layouts/ProjectWorkspace.vue'
import ProjectModulePlaceholder from '@/views/ProjectModulePlaceholder.vue'
import ModelSettingsView from '@/views/ModelSettingsView.vue'
import AnalysisView from '@/features/analysis/AnalysisView.vue'
import QuestionWizardView from '@/features/analysis/QuestionWizardView.vue'
import RequirementsView from '@/features/requirements/RequirementsView.vue'

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
        path: '/settings/model',
        name: 'model-settings',
        component: ModelSettingsView,
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
            component: AnalysisView,
          },
          {
            path: 'questions',
            name: 'project-questions',
            component: QuestionWizardView,
          },
          {
            path: 'requirements',
            name: 'project-requirements',
            component: RequirementsView,
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
